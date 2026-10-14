import { useCallback, useMemo, useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import usePolling from '../hooks/usePolling.js';
import { timeAgo } from '../utils/time.js';
import * as api from '../api.js';

const FILTERS = [
  { id: 'all', label: 'All' },
  { id: 'events', label: 'Events' },
  { id: 'dead', label: 'Dead letters' },
];

export default function EventLogPage({ getToken }) {
  const load = useCallback(() => api.listRecentEvents(getToken()), [getToken]);
  const { data: events, error, setError, loading } = usePolling(load, 3000);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');

  const counts = useMemo(
    () => ({
      all: events?.length ?? 0,
      events: events?.filter((e) => !e.deadLetter).length ?? 0,
      dead: events?.filter((e) => e.deadLetter).length ?? 0,
    }),
    [events]
  );

  const filtered = useMemo(() => {
    if (!events) return [];
    const q = search.trim().toLowerCase();
    return events.filter((e) => {
      if (filter === 'events' && e.deadLetter) return false;
      if (filter === 'dead' && !e.deadLetter) return false;
      if (!q) return true;
      return [e.type, e.source, e.topic, e.key, e.error].some((f) => (f || '').toLowerCase().includes(q));
    });
  }, [events, search, filter]);

  return (
    <>
      <PageHeader
        title="Event log"
        subtitle="The latest events on every events.* Kafka topic, including dead letters. Refreshes every 3s."
        search={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search by type, service, topic or error"
      />
      <div className="filter-bar">
        <div className="segmented">
          {FILTERS.map((f) => (
            <button
              key={f.id}
              type="button"
              className={`segmented-item ${filter === f.id ? 'segmented-item--active' : ''}`}
              onClick={() => setFilter(f.id)}
            >
              {f.label}
              <span className="segmented-count">{counts[f.id]}</span>
            </button>
          ))}
        </div>
      </div>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      <div className="table-card">
        <table className="data-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Source</th>
              <th>Topic</th>
              <th>Key</th>
            </tr>
          </thead>
          <tbody>
            {loading && !events && (
              <tr>
                <td colSpan={5} className="table-empty">
                  <SectionSpinner label="Loading events…" />
                </td>
              </tr>
            )}
            {events && filtered.length === 0 && (
              <tr>
                <td colSpan={5} className="table-empty">No events.</td>
              </tr>
            )}
            {filtered.map((e) => (
              <tr key={`${e.topic}-${e.partition}-${e.offset}`} className={e.deadLetter ? 'dead-row' : undefined}>
                <td className="last-sync" title={e.timestamp}>{timeAgo(e.timestamp)}</td>
                <td>
                  <div className="name-cell">
                    <span className="mono-strong">{e.type || (e.raw ? 'undecodable' : '—')}</span>
                    {e.deadLetter && <span className="dead-error" title={e.error}>{e.error || 'dead letter'}</span>}
                    {e.raw && <span className="name-secondary mono-cell">{e.raw}</span>}
                  </div>
                </td>
                <td>{e.source || '—'}</td>
                <td className="mono-cell">
                  {e.deadLetter && <span className="state-pill tone-bad">DLT</span>} {e.topic}
                </td>
                <td className="mono-cell">{e.key || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
