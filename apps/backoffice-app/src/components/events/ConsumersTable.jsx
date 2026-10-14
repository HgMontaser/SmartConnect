import { useState } from 'react';
import { formatCount } from '../../utils/kafka.js';

const STATE_TONE = { STABLE: 'tone-ok', EMPTY: 'tone-muted', DEAD: 'tone-bad' };

// Consumer groups: who is reading, and how far behind they are.
export default function ConsumersTable({ groups }) {
  const [showIdle, setShowIdle] = useState(false);
  // An empty group with no offsets left is just Kafka not having forgotten it yet.
  const idle = groups.filter((g) => g.state === 'EMPTY' && g.topics.length === 0);
  const shown = showIdle ? groups : groups.filter((g) => !idle.includes(g));

  return (
    <>
      <div className="events-toolbar">
        <span className="events-count">{shown.length} groups</span>
        {idle.length > 0 && (
          <label className="check-label">
            <input type="checkbox" checked={showIdle} onChange={(e) => setShowIdle(e.target.checked)} />
            Show {idle.length} idle group{idle.length === 1 ? '' : 's'} with nothing committed
          </label>
        )}
      </div>
      <div className="table-card">
        <table className="data-table events-table">
          <thead>
            <tr>
              <th>Group</th>
              <th>State</th>
              <th className="num">Members</th>
              <th className="num" title="Messages not yet consumed, over all its partitions">Lag</th>
              <th>Topics (lag)</th>
            </tr>
          </thead>
          <tbody>
            {shown.length === 0 && (
              <tr>
                <td colSpan={5} className="table-empty">No consumer groups.</td>
              </tr>
            )}
            {shown.map((g) => (
              <tr key={g.groupId}>
                <td className="mono-strong">{g.groupId}</td>
                <td>
                  <span className={`state-pill ${STATE_TONE[g.state] || 'tone-warn'}`}>{g.state.toLowerCase()}</span>
                </td>
                <td className="num mono-cell">{g.members}</td>
                <td className={`num mono-cell ${g.lag > 0 ? 'tone-warn' : ''}`}>{formatCount(g.lag)}</td>
                <td>
                  {g.topics.length === 0 ? (
                    <span className="muted-text">nothing committed</span>
                  ) : (
                    <div className="perm-chip-group">
                      {g.topics.map((t) => (
                        <span key={t.topic} className="perm-chip">
                          {t.topic} <span className={t.lag > 0 ? 'tone-warn' : 'tone-muted'}>({formatCount(t.lag)})</span>
                        </span>
                      ))}
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
