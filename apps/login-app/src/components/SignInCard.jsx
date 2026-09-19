export default function SignInCard({ onSignIn }) {
  return (
    <div className="card">
      <h1 className="card-heading">Sign in to SmartConnect</h1>
      <p className="card-subtext">You&rsquo;ll be redirected to the SmartConnect sign-in page.</p>
      <button type="button" className="primary-button" onClick={onSignIn}>
        Continue to sign in
      </button>
    </div>
  );
}
