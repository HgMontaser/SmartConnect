package com.smartconnect.commons.client;

/** Supplies this service's own access token for {@link ServiceClients#asService()}. */
@FunctionalInterface
public interface ServiceTokenProvider {

    String token();
}
