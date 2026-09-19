export default function RedirectingCard() {
  return (
    <div className="card">
      <h1 className="card-heading">Checking your session</h1>
      <p className="card-subtext">One moment&hellip;</p>
      <div className="loading-line">
        <span className="dot-spinner" aria-hidden="true" />
        <span>Contacting Keycloak</span>
      </div>
    </div>
  );
}
