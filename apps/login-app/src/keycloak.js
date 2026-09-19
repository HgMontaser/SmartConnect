import Keycloak from 'keycloak-js';

export const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080';
export const BUSINESS_APP_HOME_URL =
  import.meta.env.VITE_BUSINESS_APP_HOME_URL || 'http://localhost:5174/';
export const REALM = 'smartconnect';
export const CLIENT_ID = 'login-app';

const keycloak = new Keycloak({
  url: KEYCLOAK_URL,
  realm: REALM,
  clientId: CLIENT_ID,
});

export default keycloak;
