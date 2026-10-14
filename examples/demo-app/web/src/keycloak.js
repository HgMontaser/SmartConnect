import Keycloak from 'keycloak-js';

// Its own public client (../keycloak/demo-web.json), same realm - so a session from the
// login app or backoffice signs you in here too (SSO).
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: 'smartconnect',
  clientId: 'demo-web',
});

export default keycloak;
