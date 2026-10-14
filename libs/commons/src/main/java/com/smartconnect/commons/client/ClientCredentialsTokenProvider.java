package com.smartconnect.commons.client;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Client-credentials token from registration {@value ServiceClients#REGISTRATION_ID}. Kept in
 * its own class so services without the OAuth2 client starter never load OAuth2 types.
 */
public class ClientCredentialsTokenProvider implements ServiceTokenProvider {

    private final OAuth2AuthorizedClientManager clientManager;
    private final String serviceName;

    public ClientCredentialsTokenProvider(OAuth2AuthorizedClientManager clientManager, String serviceName) {
        this.clientManager = clientManager;
        this.serviceName = serviceName;
    }

    @Override
    public String token() {
        OAuth2AuthorizedClient client = clientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(ServiceClients.REGISTRATION_ID).principal(serviceName).build());
        if (client == null) {
            throw new IllegalStateException("Could not obtain a service token for " + serviceName);
        }
        return client.getAccessToken().getTokenValue();
    }
}
