import { useCallback, useEffect, useMemo, useState } from 'react';
import { SectionSpinner } from '../Spinner.jsx';
import { AlertIcon } from '../icons.jsx';
import usePolling from '../../hooks/usePolling.js';
import { initialsOf, timeAgo } from '../../utils/time.js';
import * as api from '../../api.js';

const FILTERS = [
  { id: 'all', label: 'All activity' },
  { id: 'dead', label: 'Failures' },
];
const NEW_FOR_MS = 5000;

const positionOf = (e) => `${e.topic}:${e.partition}:${e.offset}`;

// Which of a request's events tells the story best: what happened to whom (user events, the
// permission catalog) beats who did what (the audit record), which beats an event's bare name.
function rank(e) {
  if (e.type?.startsWith('user.') || (!e.type && e.topic === 'permission-catalog')) return 0;
  if (e.type === 'audit.performed') return 1;
  return 2;
}

// One line per request: its events (domain event + audit record, ...) share a correlation id.
// Failures stay on their own line.
function toActivities(events) {
  const groups = new Map();
  events.forEach((e) => {
    const key = e.deadLetter || !e.correlationId ? positionOf(e) : e.correlationId;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(e);
  });
  return [...groups.entries()]
    .map(([key, members]) => {
      const sorted = [...members].sort((a, b) => rank(a) - rank(b));
      const story = sorted[0];
      const actor =
        members.find((m) => m.type === 'audit.performed' && m.actor)?.actor ?? members.find((m) => m.actor)?.actor ?? null;
      const times = members.map((m) => new Date(m.timestamp).getTime());
      return {
        key,
        actor,
        text: story.summary || story.type || 'wrote a record',
        failed: members.some((m) => m.deadLetter),
        services: [...new Set(members.map((m) => m.source).filter(Boolean))],
        members: [...members].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp)),
        at: Math.max(...times),
        receivedAt: Math.max(0, ...members.map((m) => m.receivedAt || 0)),
      };
    })
    .sort((a, b) => b.at - a.at);
}

// What is happening on the platform, live: "admin.seed created user alice", one line per
// request, from every service's events. Pushed over the shell's socket, backed by the last 200
// records notification-service keeps.
export default function LiveFeed({ getToken, search, liveEvents = [], connected, onOpen }) {
  const load = useCallback(() => api.listRecentEvents(getToken()), [getToken]);
  const { data: recent, error, loading } = usePolling(load, 15000);
  const [filter, setFilter] = useState('all');
  const [, setTick] = useState(0);

  // Keeps "3s ago" and the just-happened highlight current.
  useEffect(() => {
    const timer = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  const activities = useMemo(() => {
    const seen = new Set();
    const events = [...liveEvents, ...(recent ?? [])].filter((e) => {
      const key = positionOf(e);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
    return toActivities(events);
  }, [liveEvents, recent]);

  const failures = activities.filter((a) => a.failed).length;
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return activities.filter((a) => {
      if (filter === 'dead' && !a.failed) return false;
      if (!q) return true;
      return [a.actor, a.text, ...a.services, ...a.members.flatMap((m) => [m.type, m.topic, m.correlationId])].some((f) =>
        (f || '').toLowerCase().includes(q)
      );
    });
  }, [activities, search, filter]);

  const now = Date.now();

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
              <span className="segmented-count">{f.id === 'dead' ? failures : activities.length}</span>
            </button>
          ))}
        </div>
        <span className="events-live">
          <span className={`live-dot ${connected ? 'live-dot--success' : 'live-dot--warn'}`} />
          {connected ? 'live' : 'reconnecting…'}
        </span>
      </div>
      {error && <div className="info-note">Couldn&apos;t load recent activity: {error}</div>}
      <div className="table-card activity-card">
        {loading && !recent && <SectionSpinner label="Loading activity…" />}
        {recent && rows.length === 0 && <p className="table-empty">{activities.length ? 'Nothing matches.' : 'Nothing has happened yet.'}</p>}
        <ol className="activity-list">
          {rows.map((a) => {
            const fresh = a.receivedAt && now - a.receivedAt < NEW_FOR_MS;
            return (
              <li key={a.key} className={`activity ${fresh ? 'activity--new' : ''} ${a.failed ? 'activity--failed' : ''}`}>
                <span className={`activity-avatar ${a.actor ? '' : 'activity-avatar--system'}`} aria-hidden="true">
                  {a.failed ? <AlertIcon /> : initialsOf(a.actor || a.services[0] || '?')}
                </span>
                <div className="activity-body">
                  <p className="activity-text">
                    {a.actor && <strong>{a.actor} </strong>}
                    {!a.actor && a.services[0] && <strong>{a.services[0]} </strong>}
                    {a.text}
                  </p>
                  <div className="activity-meta">
                    {a.services.map((s) => (
                      <span key={s} className="activity-service">{s}</span>
                    ))}
                    {a.members.map((m) => (
                      <button
                        key={positionOf(m)}
                        type="button"
                        className={`activity-event ${m.deadLetter ? 'activity-event--dlt' : ''}`}
                        onClick={() => onOpen({ topic: m.topic, partition: m.partition, offset: m.offset })}
                        title={`${m.topic} · ${m.partition}:${m.offset} - open`}
                      >
                        {m.type || m.topic}
                      </button>
                    ))}
                  </div>
                </div>
                <span className={`activity-time ${fresh ? 'activity-time--new' : ''}`} title={new Date(a.at).toLocaleString()}>
                  {timeAgo(new Date(a.at))}
                </span>
              </li>
            );
          })}
        </ol>
      </div>
    </>
  );
}
