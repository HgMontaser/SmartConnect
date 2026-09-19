package com.smartconnect.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code keycloak.admin.*} and {@code keycloak.target-realm}, seeded into Vault at
 * {@code secret/smartconnect/user-service} (see infra/vault/seed.sh). {@code admin.realm} is
 * the realm the admin-cli service account authenticates against (master); {@code
 * targetRealm} is the realm whose users this service manages (smartconnect) - these are
 * deliberately different properties because they are conceptually different realms.
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
        private String username;
        private String password;

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

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
