import { useCallback, useMemo, useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import usePolling from '../hooks/usePolling.js';
import { timeAgo } from '../utils/time.js';
import * as api from '../api.js';
import TraceDrawer from '../components/TraceDrawer.jsx';

const TABS = [
  { id: 'actions', label: 'Actions', hint: 'Every change made through a SmartConnect API, with the person who made it - denied and failed attempts included.' },
  { id: 'admin', label: 'Keycloak changes', hint: 'Keycloak’s own record of every change to users, roles and clients (30 days), including changes made in the Keycloak console. Backoffice changes show as service-account-user-service - see Actions for the person.' },
  { id: 'signins', label: 'Sign-ins', hint: 'Logins, logouts and failed attempts (30 days).' },
];

function statusTone(status) {
  if (status < 300) return 'tone-ok';
  if (status < 500) return 'tone-warn';
  return 'tone-bad';
}

export default function AuditPage({ getToken, canTrace }) {
  const [tab, setTab] = useState('actions');
  const [tracing, setTracing] = useState(null);
  const traceColumn = canTrace && tab === 'actions';
  const [search, setSearch] = useState('');

  const loader = useCallback(() => {
    if (tab === 'actions') return api.listAuditActions(getToken());
    if (tab === 'admin') return api.listAdminChanges(getToken());
    return api.listSignIns(getToken());
  }, [tab, getToken]);
  const { data, error, setError, loading } = usePolling(loader, 10000);

  const rows = useMemo(() => {
    if (!data) return [];
    const q = search.trim().toLowerCase();
    if (!q) return data;
    return data.filter((row) => JSON.stringify(row).toLowerCase().includes(q));
  }, [data, search]);

  const current = TABS.find((t) => t.id === tab);

  return (
    <>
      <PageHeader
        title="Audit"
        subtitle={current.hint}
        search={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search by person, action, resource or IP"
      />
      <div className="filter-bar">
        <div className="segmented">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              className={`segmented-item ${tab === t.id ? 'segmented-item--active' : ''}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      <div className="table-card">
        <table className="data-table">
          <thead>
            {tab === 'actions' && (
              <tr><th>When</th><th>Who</th><th>Action</th><th>Target</th><th>Result</th>{traceColumn && <th aria-label="Trace" />}</tr>
            )}
            {tab === 'admin' && (
              <tr><th>When</th><th>Operation</th><th>Resource</th><th>By</th><th>IP</th></tr>
            )}
            {tab === 'signins' && (
              <tr><th>When</th><th>Event</th><th>User</th><th>App</th><th>IP</th></tr>
            )}
          </thead>
          <tbody>
            {loading && !data && (
              <tr><td colSpan={traceColumn ? 6 : 5} className="table-empty"><SectionSpinner label="Loading audit trail…" /></td></tr>
            )}
            {data && rows.length === 0 && (
              <tr><td colSpan={traceColumn ? 6 : 5} className="table-empty">Nothing recorded yet.</td></tr>
            )}
            {tab === 'actions' &&
              rows.map((e) => (
                <tr key={e.eventId}>
                  <td className="last-sync" title={e.timestamp}>{timeAgo(e.timestamp)}</td>
                  <td>
                    <span className="name-primary">{e.record.actor}</span>
                  </td>
                  <td className="mono-cell">{e.record.action}<span className="name-secondary"> · {e.record.service}</span></td>
                  <td className="mono-cell">
                    {Object.entries(e.record.targets || {}).map(([k, v]) => `${k}=${v}`).join(', ') || '—'}
                  </td>
                  <td><span className={`state-pill ${statusTone(e.record.status)}`}>{e.record.status}</span></td>
                  {traceColumn && (
                    <td className="trace-cell">
                      {e.correlationId && (
                        <button type="button" className="trace-button" onClick={() => setTracing(e.correlationId)} title="Follow this request through every service">
                          Trace
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            {tab === 'admin' &&
              rows.map((e, i) => (
                <tr key={`${e.at}-${i}`}>
                  <td className="last-sync" title={e.at}>{timeAgo(e.at)}</td>
                  <td><span className={`state-pill ${e.operation === 'DELETE' ? 'tone-bad' : e.operation === 'CREATE' ? 'tone-ok' : 'tone-warn'}`}>{e.operation}</span></td>
                  <td className="mono-cell">{e.resourceType} · {e.resourcePath}</td>
                  <td>{e.actor || '—'}<span className="name-secondary"> via {e.client || '—'}</span></td>
                  <td className="mono-cell">{e.ipAddress || '—'}</td>
                </tr>
              ))}
            {tab === 'signins' &&
              rows.map((e, i) => (
                <tr key={`${e.at}-${i}`} className={e.error ? 'dead-row' : undefined}>
                  <td className="last-sync" title={e.at}>{timeAgo(e.at)}</td>
                  <td>
                    <span className="mono-strong">{e.type}</span>
                    {e.error && <span className="dead-error"> {e.error}</span>}
                  </td>
                  <td>{e.username || '—'}</td>
                  <td className="mono-cell">{e.client || '—'}</td>
                  <td className="mono-cell">{e.ipAddress || '—'}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      {tracing && <TraceDrawer correlationId={tracing} getToken={getToken} onClose={() => setTracing(null)} />}
    </>
  );
}
