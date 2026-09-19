export default function ErrorCard({ message }) {
  return (
    <div className="card">
      <h1 className="error-heading">Sign-in failed</h1>
      <p className="error-text">{message}</p>
      <button
        type="button"
        className="ghost-button"
        onClick={() => window.location.reload()}
      >
        Try again
      </button>
    </div>
  );
}
