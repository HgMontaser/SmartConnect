import { lazy, Suspense, useCallback, useMemo, useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import { LogoutIcon, TrashIcon } from '../components/icons.jsx';
import usePolling from '../hooks/usePolling.js';
import { timeAgo } from '../utils/time.js';
import { P } from '../permissions.js';
import * as api from '../api.js';

const ConfirmDialog = lazy(() => import('../components/ConfirmDialog.jsx'));

export default function SessionsPage({ getToken, can, currentSessionId, notify }) {
  const load = useCallback(() => api.listSessions(getToken()), [getToken]);
  const { data: sessions, error, setError, loading, refresh } = usePolling(load, 15000);
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState(null);
  const canWrite = can(P.SESSIONS_WRITE);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!sessions) return [];
    if (!q) return sessions;
    return sessions.filter((s) => [s.username, s.ipAddress, ...(s.clients || [])].some((f) => (f || '').toLowerCase().includes(q)));
  }, [sessions, search]);

  return (
    <>
      <PageHeader
        title="Sessions"
        subtitle="Active sign-ins across all SmartConnect apps (Keycloak SSO sessions)."
        search={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search by user, IP or app"
      />
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      <div className="table-card">
        <table className="data-table">
          <thead>
            <tr>
              <th>User</th>
              <th>IP address</th>
              <th>Apps</th>
              <th>Started</th>
              <th>Last active</th>
              {canWrite && <th aria-label="Actions" />}
            </tr>
          </thead>
          <tbody>
            {loading && !sessions && (
              <tr>
                <td colSpan={6} className="table-empty">
                  <SectionSpinner label="Loading sessions…" />
                </td>
              </tr>
            )}
            {sessions && filtered.length === 0 && (
              <tr>
                <td colSpan={6} className="table-empty">No active sessions.</td>
              </tr>
            )}
            {filtered.map((s) => {
              const mine = s.id === currentSessionId;
              return (
                <tr key={s.id}>
                  <td>
                    <span className="name-primary">
                      {s.username}
                      {mine && <span className="you-tag">this session</span>}
                    </span>
                  </td>
                  <td className="mono-cell">{s.ipAddress || '—'}</td>
                  <td>
                    <div className="perm-chip-group">
                      {s.clients.map((c) => (
                        <span key={c} className="perm-chip">{c}</span>
                      ))}
                    </div>
                  </td>
                  <td className="last-sync">{timeAgo(s.startedAt)}</td>
                  <td className="last-sync">{timeAgo(s.lastAccessAt)}</td>
                  {canWrite && (
                    <td>
                      <div className="row-actions">
                        <button
                          type="button"
                          className="icon-button icon-button--danger"
                          title={mine ? 'This is your current session' : 'Revoke this session'}
                          aria-label="Revoke session"
                          disabled={mine}
                          onClick={() =>
                            setDialog({
                              title: 'Revoke session',
                              message: `End ${s.username}'s session from ${s.ipAddress || 'unknown IP'}? They'll have to sign in again in those apps.`,
                              confirmLabel: 'Revoke',
                              danger: true,
                              onConfirm: async () => {
                                await api.revokeSession(s.id, getToken());
                                notify(`Session of ${s.username} revoked`);
                                refresh();
                              },
                            })
                          }
                        >
                          <TrashIcon />
                        </button>
                        <button
                          type="button"
                          className="icon-button icon-button--danger"
                          title="Sign this user out everywhere"
                          aria-label="Sign out everywhere"
                          disabled={mine}
                          onClick={() =>
                            setDialog({
                              title: 'Sign out everywhere',
                              message: `End every session of ${s.username}, on all devices and apps?`,
                              confirmLabel: 'Sign out everywhere',
                              danger: true,
                              onConfirm: async () => {
                                await api.signOutEverywhere(s.userId, getToken());
                                notify(`${s.username} signed out everywhere`);
                                refresh();
                              },
                            })
                          }
                        >
                          <LogoutIcon />
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {dialog && (
        <Suspense fallback={<SectionSpinner overlay />}>
          <ConfirmDialog {...dialog} onClose={() => setDialog(null)} />
        </Suspense>
      )}
    </>
  );
}
