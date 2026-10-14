import { useCallback, useMemo, useState } from 'react';
import { SectionSpinner } from '../Spinner.jsx';
import usePolling from '../../hooks/usePolling.js';
import { timeAgo } from '../../utils/time.js';
import * as api from '../../api.js';

const FILTERS = [
  { id: 'all', label: 'All' },
  { id: 'events', label: 'Events' },
  { id: 'dead', label: 'Dead letters' },
];

// The latest records on every topic as they arrive (notification-service tails them all).
export default function LiveFeed({ getToken, search, onOpen }) {
  const load = useCallback(() => api.listRecentEvents(getToken()), [getToken]);
  const { data: events, error, loading } = usePolling(load, 3000);
  const [filter, setFilter] = useState('all');

  const counts = useMemo(
    () => ({
      all: events?.length ?? 0,
      events: events?.filter((e) => !e.deadLetter).length ?? 0,
      dead: events?.filter((e) => e.deadLetter).length ?? 0,
    }),
    [events]
  );

  const rows = useMemo(() => {
    if (!events) return [];
    const q = search.trim().toLowerCase();
    return events.filter((e) => {
      if (filter === 'events' && e.deadLetter) return false;
      if (filter === 'dead' && !e.deadLetter) return false;
      if (!q) return true;
      return [e.type, e.source, e.topic, e.key, e.error, e.correlationId].some((f) => (f || '').toLowerCase().includes(q));
    });
  }, [events, search, filter]);

  return (
    <>
      <div className="events-toolbar">
        <div className="segmented segmented--small" role="group" aria-label="Show">
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
        <span className="events-live">
          <span className="live-dot live-dot--success" /> live · every 3s
        </span>
      </div>
      {error && <div className="info-note">Couldn&apos;t load the live feed: {error}</div>}
      <div className="table-card">
        <table className="data-table events-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Topic</th>
              <th>Source</th>
              <th>Key</th>
            </tr>
          </thead>
          <tbody>
            {loading && !events && (
              <tr>
                <td colSpan={5} className="table-empty"><SectionSpinner label="Loading events…" /></td>
              </tr>
            )}
            {events && rows.length === 0 && (
              <tr>
                <td colSpan={5} className="table-empty">No events.</td>
              </tr>
            )}
            {rows.map((e) => (
              <tr
                key={`${e.topic}-${e.partition}-${e.offset}`}
                className={`clickable-row ${e.deadLetter ? 'dead-row' : ''}`}
                onClick={() => onOpen({ topic: e.topic, partition: e.partition, offset: e.offset })}
              >
                <td className="last-sync" title={e.timestamp}>{timeAgo(e.timestamp)}</td>
                <td>
                  <div className="name-cell">
                    <span className="mono-strong">{e.type || (e.raw ? 'not an event' : '—')}</span>
                    {e.deadLetter && <span className="dead-error" title={e.error}>{e.error || 'dead letter'}</span>}
                  </div>
                </td>
                <td className="mono-cell">
                  {e.deadLetter && <span className="state-pill tone-bad">DLT</span>} {e.topic}
                  <span className="cell-sub">{e.partition}:{e.offset}</span>
                </td>
                <td>{e.source || '—'}</td>
                <td className="mono-cell truncate-cell">{e.key || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
