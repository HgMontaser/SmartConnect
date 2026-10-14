import { LockIcon } from './icons.jsx';

export default function NoAccess({ onSignOut }) {
  return (
    <div className="centered-state">
      <span className="no-access-icon">
        <LockIcon />
      </span>
      <h1 className="card-heading">No backoffice access</h1>
      <p className="card-subtext">
        You're signed in, but none of your roles grant a backoffice permission. Ask an administrator to give you a role
        with access to users, roles or permissions.
      </p>
      <button type="button" className="ghost-button" onClick={onSignOut}>
        Sign out
      </button>
    </div>
  );
}
