import { useEffect, useRef, useState } from 'react';
import keycloak from './keycloak.js';
import SsoHandshake from './components/SsoHandshake.jsx';
import ErrorCard from './components/ErrorCard.jsx';
import RedirectingCard from './components/RedirectingCard.jsx';
import SignInCard from './components/SignInCard.jsx';

export default function App() {
  // 'checking' | 'ready' | 'authenticated' | 'error'
  const [status, setStatus] = useState('checking');
  const [errorMessage, setErrorMessage] = useState('');
  const initStarted = useRef(false);

  useEffect(() => {
    // Guard against React 18 StrictMode's double-invoke of effects in dev —
    // keycloak-js throws if init() is called twice on the same instance. Deliberately no
    // `cancelled`/cleanup flag here: this is the app's root component, mounted exactly once
    // for the page's lifetime, and StrictMode's synchronous mount->cleanup->mount replay
    // would otherwise mark the very init() call we just kicked off as "cancelled" before its
    // promise ever resolves, silently dropping the setStatus() call below.
    if (initStarted.current) return;
    initStarted.current = true;

    // No `onLoad: 'login-required'` here on purpose: that mode redirects to Keycloak
    // automatically as soon as init() runs, with no user click involved. Some browsers
    // (Brave in particular, with Shields on) silently block that kind of unsolicited,
    // script-triggered top-level navigation — the page just hangs with no console error.
    // Redirecting from a real button click instead (see handleSignIn) ties the navigation
    // to genuine user activation, which every browser reliably allows. This only skips
    // the auto-redirect on a fresh load; returning from Keycloak with an auth code in the
    // URL is still handled automatically below, regardless of onLoad.
    keycloak
      .init({
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        setStatus(authenticated ? 'authenticated' : 'ready');
      })
      .catch((error) => {
        // Log the real cause to the console instead of swallowing it — this is the
        // only diagnostic trail available once the UI shows the generic error card.
        console.error('[login-app] keycloak.init() failed:', error);
        setErrorMessage(
          'We could not reach the SmartConnect sign-in service. Please try again in a moment.'
        );
        setStatus('error');
      });
  }, []);

  const handleSignIn = () => {
    keycloak.login().catch((error) => {
      console.error('[login-app] keycloak.login() failed:', error);
      setErrorMessage('We could not start sign-in. Please try again in a moment.');
      setStatus('error');
    });
  };

  return (
    <div className="app-shell">
      {status === 'checking' && <RedirectingCard />}
      {status === 'ready' && <SignInCard onSignIn={handleSignIn} />}
      {status === 'authenticated' && <SsoHandshake tokenParsed={keycloak.tokenParsed} />}
      {status === 'error' && <ErrorCard message={errorMessage} />}
    </div>
  );
}
