package com.smartconnect.commons.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.BlockingLoadBalancerInterceptor;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceClientsAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServiceClientsAutoConfiguration.class))
            .withBean(DeferringLoadBalancerInterceptor.class, 
                    () -> new DeferringLoadBalancerInterceptor(new DefaultListableBeanFactory().getBeanProvider(BlockingLoadBalancerInterceptor.class)));

    @Test
    void worksWithoutTheOAuth2ClientLibrary() {
        // Platform services don't ship spring-security-oauth2-client; loading must not touch it.
        runner.withClassLoader(new FilteredClassLoader("org.springframework.security.oauth2.client"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ServiceClients.class);
                    assertThatThrownBy(() -> context.getBean(ServiceClients.class).asService())
                            .hasMessageContaining("oauth2-client");
                });
    }

    @Test
    void asCallerNeedsAUserRequest() {
        runner.run(context -> assertThatThrownBy(() -> context.getBean(ServiceClients.class).asCaller().get().uri("http://x/").retrieve().toBodilessEntity())
                .hasMessageContaining("asService()"));
    }
}
