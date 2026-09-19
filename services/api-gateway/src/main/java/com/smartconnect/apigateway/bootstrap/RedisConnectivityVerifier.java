package com.smartconnect.apigateway.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiting is the one thing api-gateway can never silently degrade on - if Redis isn't
 * reachable at boot we want a loud startup failure, not a gateway that quietly lets every
 * request through unlimited. Lettuce (the default reactive Redis client) connects lazily on
 * first command, so without this check a dead Redis would only surface on the first real
 * request.
 */
@Component
public class RedisConnectivityVerifier implements ApplicationRunner {

    private final ReactiveRedisConnectionFactory connectionFactory;

    public RedisConnectivityVerifier(ReactiveRedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        String pong = connectionFactory.getReactiveConnection().ping().block(Duration.ofSeconds(5));
        if (pong == null || !pong.equalsIgnoreCase("PONG")) {
            throw new IllegalStateException("Redis did not respond to PING at startup - rate limiting store unreachable");
        }
    }
}
