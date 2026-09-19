export default function NotSignedIn({ onSignIn }) {
  return (
    <div className="centered-state">
      <h1 className="card-heading">Not signed in</h1>
      <p className="card-subtext">
        We couldn't find an active SmartConnect session. Sign in with Keycloak to manage users.
      </p>
      <button type="button" className="primary-button" onClick={onSignIn}>
        Sign in
      </button>
    </div>
  );
}
