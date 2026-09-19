import Keycloak from 'keycloak-js';

export const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080';
export const REALM = 'smartconnect';
export const CLIENT_ID = 'backoffice-app';

const keycloak = new Keycloak({
  url: KEYCLOAK_URL,
  realm: REALM,
  clientId: CLIENT_ID,
});

export default keycloak;
