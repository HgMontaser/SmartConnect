import { useEffect, useRef, useState } from 'react';
import keycloak from './keycloak.js';
import BackofficeShell from './components/BackofficeShell.jsx';
import NotSignedIn from './components/NotSignedIn.jsx';

export default function App() {
  // 'checking' | 'authenticated' | 'unauthenticated' | 'error'
  const [status, setStatus] = useState('checking');
  const [errorMessage, setErrorMessage] = useState('');
  const initStarted = useRef(false);

  useEffect(() => {
    // Guard against React 18 StrictMode's double-invoke of effects in dev —
    // keycloak-js throws if init() is called twice on the same instance. Deliberately no
    // `cancelled`/cleanup flag here: this is the app's root component, mounted exactly once
    // for the page's lifetime, and StrictMode's synchronous mount->cleanup->mount replay
    // would otherwise mark the very init() call we just kicked off as "cancelled" before its
    // promise ever resolves, silently dropping every setStatus() call below and leaving the
    // UI stuck on "Checking your session" forever.
    if (initStarted.current) return;
    initStarted.current = true;

    keycloak
      .init({
        // Silent SSO check only — this app never forces a login redirect on load.
        // login-app is the one place that does that; here, if a Keycloak session
        // already exists (e.g. from login-app in another tab) check-sso picks it
        // up with no visible redirect flash. If there's no session, it just
        // resolves `authenticated: false` and we show a "Not signed in" state.
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
      })
      .then((authenticated) => {
        if (authenticated) {
          keycloak.onTokenExpired = () => {
            keycloak.updateToken(30).catch(() => {
              // Refresh failed (e.g. the SSO session ended elsewhere) — drop back
              // to "not signed in" rather than letting every request start failing.
              setStatus('unauthenticated');
            });
          };
          setStatus('authenticated');
        } else {
          setStatus('unauthenticated');
        }
      })
      .catch((error) => {
        console.error('[backoffice-app] keycloak.init() failed:', error);
        setErrorMessage('Could not reach the SmartConnect sign-in service. Please try again in a moment.');
        setStatus('error');
      });
  }, []);

  if (status === 'checking') {
    return (
      <div className="centered-state">
        <div className="dot-spinner" />
        <p className="centered-state-text">Checking your session…</p>
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <NotSignedIn onSignIn={() => keycloak.login()} />;
  }

  if (status === 'error') {
    return (
      <div className="centered-state">
        <h1 className="error-heading">Something went wrong</h1>
        <p className="error-text">{errorMessage}</p>
        <button type="button" className="ghost-button" onClick={() => window.location.reload()}>
          Try again
        </button>
      </div>
    );
  }

  return <BackofficeShell keycloak={keycloak} />;
}
