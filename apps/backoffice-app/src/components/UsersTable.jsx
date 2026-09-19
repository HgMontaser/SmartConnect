import { timeAgo } from '../utils/time.js';
import { EditIcon, TrashIcon } from './icons.jsx';

function displayName(user) {
  const full = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
  return full || user.username || '—';
}

function RolePill({ role }) {
  const isAdmin = role === 'admin';
  return (
    <span
      className="role-pill"
      style={{
        color: isAdmin ? 'var(--accent)' : 'var(--text-2)',
        background: isAdmin ? 'rgba(45, 212, 191, 0.12)' : 'rgba(139, 149, 165, 0.12)',
      }}
    >
      {role}
    </span>
  );
}

function StatusPill({ enabled }) {
  if (enabled === undefined || enabled === null) {
    return <span className="status-pill status-pill--unknown">Unknown</span>;
  }
  return enabled ? (
    <span className="status-pill status-pill--active">Active</span>
  ) : (
    <span className="status-pill status-pill--suspended">Suspended</span>
  );
}

export default function UsersTable({ users, loading, onEdit, onDelete }) {
  return (
    <div className="table-card">
      <table className="users-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Role</th>
            <th>Status</th>
            <th>Last sync</th>
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {loading && (
            <tr>
              <td colSpan={6} className="table-empty">
                Loading users…
              </td>
            </tr>
          )}
          {!loading && users.length === 0 && (
            <tr>
              <td colSpan={6} className="table-empty">
                No users found.
              </td>
            </tr>
          )}
          {!loading &&
            users.map((user) => (
              <tr key={user.id}>
                <td>{displayName(user)}</td>
                <td className="mono-cell">{user.email || '—'}</td>
                <td>
                  <div className="role-pill-group">
                    {(user.realmRoles || []).map((role) => (
                      <RolePill key={role} role={role} />
                    ))}
                  </div>
                </td>
                <td>
                  <StatusPill enabled={user.enabled} />
                </td>
                <td>
                  {user.justUpdated ? (
                    <span className="last-sync last-sync--live">
                      <span className="live-dot live-dot--accent" />
                      {timeAgo(user.lastSyncAt)}
                    </span>
                  ) : (
                    <span className="last-sync">{user.lastSyncAt ? timeAgo(user.lastSyncAt) : '—'}</span>
                  )}
                </td>
                <td>
                  <div className="row-actions">
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => onEdit(user)}
                      aria-label="Edit user"
                    >
                      <EditIcon />
                    </button>
                    <button
                      type="button"
                      className="icon-button icon-button--danger"
                      onClick={() => onDelete(user)}
                      aria-label="Delete user"
                    >
                      <TrashIcon />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
        </tbody>
      </table>
    </div>
  );
}
