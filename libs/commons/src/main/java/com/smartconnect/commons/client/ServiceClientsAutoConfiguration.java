package com.smartconnect.commons.client;

import com.smartconnect.commons.correlation.CorrelationIdClientHttpRequestInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Provides {@link ServiceClients} to servlet services that have the Consul starters (hence the
 * load balancer) on the classpath. The load-balanced builder is private to it rather than a
 * {@code RestClient.Builder} bean, so a service's own calls to external URLs are never
 * accidentally routed through Consul. Client-credentials support switches on only when the
 * service has the OAuth2 client starter and a registration configured; OAuth2 types are
 * referenced only from the nested configuration, so services without it never load them.
 */
@AutoConfiguration(afterName = {
        "org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RestClient.class, DeferringLoadBalancerInterceptor.class})
@ConditionalOnBean(DeferringLoadBalancerInterceptor.class)
public class ServiceClientsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceClients serviceClients(DeferringLoadBalancerInterceptor loadBalancer,
                                         ObjectProvider<ServiceTokenProvider> serviceToken,
                                         ObjectProvider<ObservationRegistry> observations,
                                         @Value("${spring.application.name:service}") String serviceName) {
        // The observation registry makes each call a client span and propagates the trace
        // (traceparent header) to the callee, so traces continue across services.
        RestClient.Builder builder = RestClient.builder()
                .observationRegistry(observations.getIfAvailable(() -> ObservationRegistry.NOOP))
                .requestInterceptor(new CorrelationIdClientHttpRequestInterceptor())
                .requestInterceptor(loadBalancer);
        return new ServiceClients(builder, serviceToken.getIfAvailable(), serviceName);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.client.registration.ClientRegistrationRepository")
    @ConditionalOnBean(type = "org.springframework.security.oauth2.client.registration.ClientRegistrationRepository")
    static class ClientCredentialsConfiguration {

        /** Not tied to an HTTP request, so it also works from jobs and Kafka listeners. */
        @Bean
        @ConditionalOnMissingBean
        public OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
                ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
            AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                    new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
            manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
            return manager;
        }

        @Bean
        @ConditionalOnMissingBean
        public ServiceTokenProvider clientCredentialsTokenProvider(
                OAuth2AuthorizedClientManager manager, @Value("${spring.application.name:service}") String serviceName) {
            return new ClientCredentialsTokenProvider(manager, serviceName);
        }
    }
}
