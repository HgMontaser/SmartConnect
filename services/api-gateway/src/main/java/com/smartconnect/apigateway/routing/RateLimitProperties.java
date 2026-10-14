package com.smartconnect.apigateway.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token-bucket settings applied to every discovered route. Lives in Consul KV
 * ({@code config/api-gateway/data}); editing it there rebinds this bean and refreshes the
 * routes, so new limits apply without a restart.
 */
@ConfigurationProperties(prefix = "smartconnect.gateway.rate-limit")
public class RateLimitProperties {

    private int replenishRate = 10;
    private int burstCapacity = 20;
    private int requestedTokens = 1;

    public int getReplenishRate() {
        return replenishRate;
    }

    public void setReplenishRate(int replenishRate) {
        this.replenishRate = replenishRate;
    }

    public int getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }

    public int getRequestedTokens() {
        return requestedTokens;
    }

    public void setRequestedTokens(int requestedTokens) {
        this.requestedTokens = requestedTokens;
    }
}
