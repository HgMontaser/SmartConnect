import { timeAgo } from '../utils/time.js';
import RolePill from './RolePill.jsx';
import { SectionSpinner } from './Spinner.jsx';
import { EditIcon, KeyIcon, PauseIcon, PlayIcon, TrashIcon } from './icons.jsx';

function displayName(user) {
  const full = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
  return full || user.username || '—';
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

export default function UsersTable({ users, loading, currentUserId, canWrite, canDelete, onEdit, onResetPassword, onToggleEnabled, onDelete }) {
  const showActions = canWrite || canDelete;
  const columns = showActions ? 6 : 5;

  return (
    <div className="table-card">
      <table className="data-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Roles</th>
            <th>Status</th>
            <th>Last sync</th>
            {showActions && <th aria-label="Actions" />}
          </tr>
        </thead>
        <tbody>
          {loading && (
            <tr>
              <td colSpan={columns} className="table-empty">
                <SectionSpinner label="Loading users…" />
              </td>
            </tr>
          )}
          {!loading && users.length === 0 && (
            <tr>
              <td colSpan={columns} className="table-empty">
                No users found.
              </td>
            </tr>
          )}
          {!loading &&
            users.map((user) => {
              const isSelf = user.id === currentUserId;
              return (
                <tr key={user.id}>
                  <td>
                    <div className="name-cell">
                      <span className="name-primary">
                        {displayName(user)}
                        {isSelf && <span className="you-tag">you</span>}
                      </span>
                      <span className="name-secondary">@{user.username}</span>
                    </div>
                  </td>
                  <td className="mono-cell">{user.email || '—'}</td>
                  <td>
                    <div className="role-pill-group">
                      {(user.realmRoles || []).length === 0 && <span className="muted-text">No roles</span>}
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
                  {showActions && (
                    <td>
                      <div className="row-actions">
                        {canWrite && (
                          <>
                            <button type="button" className="icon-button" onClick={() => onEdit(user)} title="Edit" aria-label="Edit user">
                              <EditIcon />
                            </button>
                            <button
                              type="button"
                              className="icon-button"
                              onClick={() => onResetPassword(user)}
                              title="Reset password"
                              aria-label="Reset password"
                            >
                              <KeyIcon />
                            </button>
                            <button
                              type="button"
                              className="icon-button"
                              onClick={() => onToggleEnabled(user)}
                              disabled={isSelf}
                              title={isSelf ? "You can't suspend yourself" : user.enabled ? 'Suspend' : 'Reactivate'}
                              aria-label={user.enabled ? 'Suspend user' : 'Reactivate user'}
                            >
                              {user.enabled ? <PauseIcon /> : <PlayIcon />}
                            </button>
                          </>
                        )}
                        {canDelete && (
                          <button
                            type="button"
                            className="icon-button icon-button--danger"
                            onClick={() => onDelete(user)}
                            disabled={isSelf}
                            title={isSelf ? "You can't delete yourself" : 'Delete'}
                            aria-label="Delete user"
                          >
                            <TrashIcon />
                          </button>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              );
            })}
        </tbody>
      </table>
    </div>
  );
}
