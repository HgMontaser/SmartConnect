package com.smartconnect.commons.discovery;

/**
 * Consul service metadata through which a service asks api-gateway for routes. Set by
 * {@link ServiceDiscoveryDefaults} from the service's own {@code smartconnect.gateway.*}
 * properties, read by the gateway's discovered-routes locator.
 */
public final class GatewayRouteMetadata {

    /** Comma-separated gateway path patterns, e.g. {@code /api/billing/**}. Blank = not routed. */
    public static final String ROUTES = "smartconnect-routes";

    /** {@code http} (default) or {@code ws} for WebSocket endpoints. */
    public static final String SCHEME = "smartconnect-scheme";

    /** Optional response timeout for this service's routes, e.g. {@code 30s}. Blank = gateway default. */
    public static final String TIMEOUT = "smartconnect-timeout";

    private GatewayRouteMetadata() {
    }
}
