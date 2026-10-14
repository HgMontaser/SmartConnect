import { useCallback, useEffect, useMemo, useState } from 'react';
import { SectionSpinner } from '../Spinner.jsx';
import { RefreshIcon } from '../icons.jsx';
import * as api from '../../api.js';
import { timeAgo } from '../../utils/time.js';
import { formatBytes } from '../../utils/kafka.js';

const PAGE = 50;

// One topic's records, newest or oldest first, a page at a time.
export default function TopicMessages({ topic, getToken, search, onOpen }) {
  const [partition, setPartition] = useState('');
  const [order, setOrder] = useState('newest');
  const [messages, setMessages] = useState([]);
  const [next, setNext] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(
    async (cursor) => {
      setLoading(true);
      try {
        const page = await api.listTopicMessages(topic.name, { partition, order, limit: PAGE, cursor }, getToken());
        setMessages((prev) => (cursor ? [...prev, ...page.messages] : page.messages));
        setNext(page.next);
        setError(null);
      } catch (err) {
        setError(err.message || 'Failed to load.');
      } finally {
        setLoading(false);
      }
    },
    [topic.name, partition, order, getToken]
  );

  useEffect(() => {
    setMessages([]);
    setNext(null);
    load(null);
  }, [load]);

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return messages;
    return messages.filter((m) =>
      [m.key, m.value, m.envelope?.type, m.envelope?.source, m.envelope?.correlationId].some((f) => (f || '').toLowerCase().includes(q))
    );
  }, [messages, search]);

  return (
    <>
      <div className="events-toolbar">
        <div className="segmented segmented--small" role="group" aria-label="Order">
          {[
            { id: 'newest', label: 'Newest first' },
            { id: 'oldest', label: 'Oldest first' },
          ].map((o) => (
            <button
              key={o.id}
              type="button"
              className={`segmented-item ${order === o.id ? 'segmented-item--active' : ''}`}
              onClick={() => setOrder(o.id)}
            >
              {o.label}
            </button>
          ))}
        </div>
        <select className="select-input select-input--small" value={partition} onChange={(e) => setPartition(e.target.value)} aria-label="Partition">
          <option value="">All partitions</option>
          {Array.from({ length: topic.partitions }, (_, p) => (
            <option key={p} value={p}>Partition {p}</option>
          ))}
        </select>
        <button type="button" className="icon-button" onClick={() => load(null)} aria-label="Reload" title="Reload">
          <RefreshIcon />
        </button>
        <span className="events-count">
          {rows.length !== messages.length ? `${rows.length} of ${messages.length} loaded match` : `${messages.length} of ${topic.messages.toLocaleString()} loaded`}
        </span>
      </div>
      {error && <div className="info-note">Couldn&apos;t read {topic.name}: {error}</div>}
      <div className="table-card">
        <table className="data-table events-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Source</th>
              <th>Key</th>
              <th className="num">Partition · offset</th>
              <th className="num">Size</th>
            </tr>
          </thead>
          <tbody>
            {loading && messages.length === 0 && (
              <tr>
                <td colSpan={6} className="table-empty"><SectionSpinner label={`Reading ${topic.name}…`} /></td>
              </tr>
            )}
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={6} className="table-empty">{messages.length ? 'Nothing loaded matches the search.' : 'This topic is empty.'}</td>
              </tr>
            )}
            {rows.map((m) => (
              <tr
                key={`${m.partition}-${m.offset}`}
                className={`clickable-row ${topic.deadLetter ? 'dead-row' : ''}`}
                onClick={() => onOpen({ topic: topic.name, partition: m.partition, offset: m.offset })}
              >
                <td className="last-sync" title={m.timestamp}>{timeAgo(m.timestamp)}</td>
                <td><span className="mono-strong">{m.envelope?.type || (m.binary ? 'binary' : 'not an event')}</span></td>
                <td>{m.envelope?.source || '—'}</td>
                <td className="mono-cell truncate-cell">{m.key || '—'}</td>
                <td className="num mono-cell">{m.partition} · {m.offset}</td>
                <td className="num mono-cell">{formatBytes(m.size)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {next && (
        <div className="load-more">
          <button type="button" className="ghost-button" onClick={() => load(next)} disabled={loading}>
            {loading ? 'Loading…' : order === 'newest' ? 'Load older' : 'Load newer'}
          </button>
        </div>
      )}
    </>
  );
}
