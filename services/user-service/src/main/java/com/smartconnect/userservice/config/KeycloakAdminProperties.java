package com.smartconnect.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code keycloak.admin.*} and {@code keycloak.target-realm}, seeded into Vault at
 * {@code secret/smartconnect/user-service} (see infra/vault/seed.sh). {@code admin.realm} is
 * the realm the service account authenticates in; {@code targetRealm} is the realm whose
 * users this service manages. Both are smartconnect today, but they stay separate properties
 * because they are conceptually different.
 */
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakAdminProperties {

    private final Admin admin = new Admin();
    private String targetRealm;

    public Admin getAdmin() {
        return admin;
    }

    public String getTargetRealm() {
        return targetRealm;
    }

    public void setTargetRealm(String targetRealm) {
        this.targetRealm = targetRealm;
    }

    public static class Admin {
        private String serverUrl;
        private String realm;
        private String clientId;
        private String clientSecret;

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}
