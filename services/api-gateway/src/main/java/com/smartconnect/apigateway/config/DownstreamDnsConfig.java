package com.smartconnect.apigateway.config;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Downstream services get a new container IP every time they're recreated. Reactor Netty's
 * resolver would otherwise keep serving the old address from its cache, so the gateway kept
 * answering 500 "connection refused" long after a restarted service was healthy again.
 * Capping the cache TTL makes the gateway follow a recreated container within seconds.
 */
@Configuration
public class DownstreamDnsConfig {

    private static final Duration MAX_DNS_CACHE_TTL = Duration.ofSeconds(5);

    @Bean
    public HttpClientCustomizer shortLivedDnsCache() {
        return httpClient -> httpClient.resolver(spec -> spec.cacheMaxTimeToLive(MAX_DNS_CACHE_TTL));
    }
}
