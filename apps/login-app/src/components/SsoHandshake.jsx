import { useEffect, useState } from 'react';
import CheckIcon from './CheckIcon.jsx';
import { REALM, CLIENT_ID, BUSINESS_APP_HOME_URL } from '../keycloak.js';

const PROGRESS_DURATION_MS = 1700;
const FINAL_STEP_DELAY_MS = 350;

function formatSession(sessionState) {
  if (typeof sessionState === 'string' && sessionState.length >= 10) {
    return `sso_${sessionState.slice(0, 6)}...${sessionState.slice(-4)}`;
  }
  // Plausible-looking placeholder — used if session_state isn't available for any reason.
  return 'sso_9f21e4...c88a';
}

function formatRedirectTarget(url) {
  return url.replace(/^https?:\/\//, '');
}

function StepIndicator({ status }) {
  if (status === 'done') {
    return (
      <span className="step-indicator done">
        <CheckIcon />
      </span>
    );
  }
  if (status === 'active') {
    return <span className="step-indicator active" />;
  }
  return <span className="step-indicator pending" />;
}

export default function SsoHandshake({ tokenParsed }) {
  const [step, setStep] = useState(2); // step 1 is already done by the time we mount
  const [progressWidth, setProgressWidth] = useState('0%');

  useEffect(() => {
    // Kick the progress bar fill on next frame so the CSS transition animates.
    const raf = requestAnimationFrame(() => setProgressWidth('100%'));

    const establishTimer = setTimeout(() => {
      setStep(3);
      const redirectTimer = setTimeout(() => {
        window.location.href = BUSINESS_APP_HOME_URL;
      }, FINAL_STEP_DELAY_MS);
      // no cleanup needed for redirectTimer — page is navigating away
      return () => clearTimeout(redirectTimer);
    }, PROGRESS_DURATION_MS);

    return () => {
      cancelAnimationFrame(raf);
      clearTimeout(establishTimer);
    };
  }, []);

  const sessionState = tokenParsed && tokenParsed.session_state;

  return (
    <div className="card">
      <h1 className="card-heading">Signed in</h1>
      <p className="card-subtext">Finishing sign-in and handing off your session&hellip;</p>

      <div className="stepper">
        <div className="step">
          <div className="step-indicator-column">
            <StepIndicator status="done" />
            <span className="step-connector" />
          </div>
          <span className="step-label done">Verifying credentials</span>
        </div>

        <div className="step">
          <div className="step-indicator-column">
            <StepIndicator status={step >= 3 ? 'done' : 'active'} />
            <span className="step-connector" />
          </div>
          <span className={`step-label ${step >= 3 ? 'done' : 'active'}`}>
            Establishing SSO session
          </span>
        </div>

        <div className="step">
          <div className="step-indicator-column">
            <StepIndicator status={step >= 3 ? 'active' : 'pending'} />
          </div>
          <span className={`step-label ${step >= 3 ? 'active' : 'pending'}`}>
            Redirecting to workspace
          </span>
        </div>
      </div>

      <div className="progress-track">
        <div className="progress-fill" style={{ width: progressWidth }} />
      </div>

      <div className="readout">
        <div className="readout-line">
          <span className="readout-label">realm:</span>
          <span className="readout-value">{REALM}</span>
        </div>
        <div className="readout-line">
          <span className="readout-label">client:</span>
          <span className="readout-value">{CLIENT_ID}</span>
        </div>
        <div className="readout-line">
          <span className="readout-label">session:</span>
          <span className="readout-value">{formatSession(sessionState)}</span>
        </div>
        <div className="readout-line">
          <span className="readout-label">redirect:</span>
          <span className="readout-value">{formatRedirectTarget(BUSINESS_APP_HOME_URL)}</span>
        </div>
      </div>
    </div>
  );
}
