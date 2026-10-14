import { formatCount, formatRetention } from '../../utils/kafka.js';

// Every topic with its size, retention and who reads it.
export default function TopicsTable({ topics, onBrowse }) {
  return (
    <div className="table-card">
      <table className="data-table events-table">
        <thead>
          <tr>
            <th>Topic</th>
            <th className="num">Partitions</th>
            <th className="num">Messages</th>
            <th>Retention</th>
            <th>Read by</th>
            <th aria-label="Browse" />
          </tr>
        </thead>
        <tbody>
          {topics.length === 0 && (
            <tr>
              <td colSpan={6} className="table-empty">No topics yet.</td>
            </tr>
          )}
          {topics.map((t) => (
            <tr key={t.name} className={t.deadLetter ? 'dead-row' : undefined}>
              <td>
                <span className="mono-strong">{t.name}</span>
                {t.deadLetter && <span className="state-pill tone-bad topic-pill">DLT</span>}
              </td>
              <td className="num mono-cell">{t.partitions}</td>
              <td className="num mono-cell">{formatCount(t.messages)}</td>
              <td className="mono-cell">{formatRetention(t.retentionMs)}</td>
              <td>
                {t.readers.length === 0 ? (
                  <span className="muted-text">no consumer group</span>
                ) : (
                  <div className="perm-chip-group">
                    {t.readers.map((r) => (
                      <span key={r} className="perm-chip">{r}</span>
                    ))}
                  </div>
                )}
              </td>
              <td className="trace-cell">
                <button type="button" className="trace-button" onClick={() => onBrowse(t.name)}>
                  Messages
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
