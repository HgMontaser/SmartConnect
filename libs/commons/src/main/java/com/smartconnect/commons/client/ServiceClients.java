package com.smartconnect.commons.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

/**
 * The one way services call each other. Targets are Consul service names
 * ({@code http://user-service/api/users}), load-balanced across healthy instances, and every
 * call carries a token - pick which:
 *
 * <ul>
 *   <li>{@link #asCaller()} - forwards the current request's user token. The callee checks the
 *       <em>user's</em> permissions, so a service can never do more than the user who asked.</li>
 *   <li>{@link #asService()} - uses this service's own client-credentials token (registration
 *       {@value #REGISTRATION_ID}), for work with no user behind it: jobs, event handlers. The
 *       callee checks the permissions granted to the service account.</li>
 * </ul>
 */
public class ServiceClients {

    public static final String REGISTRATION_ID = "smartconnect";

    private final RestClient.Builder loadBalanced;
    private final ServiceTokenProvider serviceToken;
    private final String serviceName;

    public ServiceClients(RestClient.Builder loadBalanced, ServiceTokenProvider serviceToken, String serviceName) {
        this.loadBalanced = loadBalanced;
        this.serviceToken = serviceToken;
        this.serviceName = serviceName;
    }

    public RestClient asCaller() {
        return withToken(() -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt) {
                return jwt.getToken().getTokenValue();
            }
            throw new IllegalStateException("asCaller() needs an authenticated user request - use asService() for background work");
        });
    }

    public RestClient asService() {
        if (serviceToken == null) {
            throw new IllegalStateException("asService() needs spring-boot-starter-oauth2-client and "
                    + "spring.security.oauth2.client.registration." + REGISTRATION_ID + " (client_credentials) for " + serviceName);
        }
        return withToken(serviceToken::token);
    }

    private RestClient withToken(Supplier<String> token) {
        ClientHttpRequestInterceptor bearer = (request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token.get());
            return execution.execute(request, body);
        };
        // First, so a missing token fails before any instance lookup or network call.
        return loadBalanced.clone().requestInterceptors(interceptors -> interceptors.add(0, bearer)).build();
    }
}
