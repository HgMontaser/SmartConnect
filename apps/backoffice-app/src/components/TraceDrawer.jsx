import { useCallback, useEffect, useRef, useState } from 'react';
import { SectionSpinner } from './Spinner.jsx';
import { AlertIcon, CloseIcon, RefreshIcon } from './icons.jsx';
import usePolling from '../hooks/usePolling.js';
import * as api from '../api.js';

const JAEGER_URL = import.meta.env.VITE_JAEGER_URL || 'http://localhost:16686';
const LANE_HEIGHT = 26;
const LABEL_WIDTH = 132;

// SmartConnect's own code logs under com.smartconnect (abbreviated c.s. by the log pattern) and
// the audit logger; everything else is framework detail, hidden unless asked for.
function isOwnLine(line) {
  const logger = line.logger || '';
  return logger.startsWith('c.s.') || logger.startsWith('com.smartconnect') || logger.startsWith('smartconnect.');
}

function levelTone(level) {
  if (level === 'ERROR') return 'bad';
  if (level === 'WARN') return 'warn';
  return null;
}

function offsetLabel(ms) {
  if (ms < 1000) return `+${ms}ms`;
  return `+${(ms / 1000).toFixed(ms < 10000 ? 2 : 1)}s`;
}

// One request through every service: the path it took, when it reached each, and every log
// line it left on the way.
export default function TraceDrawer({ correlationId, getToken, onClose }) {
  const load = useCallback(() => api.getTrace(correlationId, getToken()), [correlationId, getToken]);
  const { data: trace, error, loading, refresh } = usePolling(load, 0);
  const [ownOnly, setOwnOnly] = useState(true);
  const [focus, setFocus] = useState(null);
  const rowRefs = useRef(new Map());
  const timelineRef = useRef(null);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const el = timelineRef.current;
    if (!el) return undefined;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, [trace]);

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const lines = trace?.lines ?? [];
  const start = lines.length ? new Date(lines[0].t).getTime() : 0;
  const span = Math.max(1, trace?.durationMs ?? 0);
  const shown = lines.map((l, i) => ({ ...l, i, offset: new Date(l.t).getTime() - start })).filter((l) => !ownOnly || isOwnLine(l) || levelTone(l.level));
  const hidden = lines.length - shown.length;
  const problems = lines.filter((l) => levelTone(l.level)).length;

  const plot = Math.max(0, width - LABEL_WIDTH - 8);
  const xAt = (offset) => LABEL_WIDTH + (offset / span) * plot;

  const jumpTo = (i) => {
    setFocus(i);
    rowRefs.current.get(i)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  };

  return (
    <div className="drawer-overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="Request trace">
        <header className="drawer-header">
          <div className="drawer-title">
            <h2 className="modal-heading">Request trace</h2>
            <span className="mono-cell drawer-id">{correlationId}</span>
          </div>
          <div className="drawer-header-actions">
            <button type="button" className="icon-button" onClick={refresh} aria-label="Reload" title="Logs arrive a few seconds late - reload">
              <RefreshIcon />
            </button>
            <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
              <CloseIcon />
            </button>
          </div>
        </header>

        <div className="drawer-body">
          {error && <div className="info-note">Couldn&apos;t load the trace: {error}</div>}
          {loading && !trace && <SectionSpinner label="Following the request…" />}
          {trace && lines.length === 0 && (
            <p className="muted-text">
              No log lines carry this id yet. Logs reach Elasticsearch a few seconds after the fact - reload in a moment.
            </p>
          )}
          {trace && lines.length > 0 && (
            <>
              <div className="trace-summary">
                <div className="trace-path" aria-label="Services in the order the request reached them">
                  {trace.services.map((s, k) => (
                    <span key={s} className="trace-path-step">
                      {k > 0 && <span className="trace-path-arrow">→</span>}
                      <span className="trace-service">{s}</span>
                    </span>
                  ))}
                </div>
                <div className="trace-facts">
                  <span><strong>{trace.durationMs != null ? offsetLabel(trace.durationMs).slice(1) : '—'}</strong> end to end</span>
                  <span><strong>{lines.length}</strong> log lines</span>
                  {problems > 0 && (
                    <span className="metric-flag tone-bad">
                      <AlertIcon />
                      {problems} warning{problems === 1 ? '' : 's'} or error{problems === 1 ? '' : 's'}
                    </span>
                  )}
                  {trace.traceIds.length > 0 && (
                    <span className="trace-jaeger" title="The distributed traces this request is part of - a Kafka consumer can start its own">
                      Jaeger
                      {trace.traceIds.map((id) => (
                        <a key={id} className="trace-link mono-cell" href={`${JAEGER_URL}/trace/${id}`} target="_blank" rel="noreferrer">
                          {id.slice(0, 8)} ↗
                        </a>
                      ))}
                    </span>
                  )}
                </div>
                {trace.truncated && <span className="muted-text">Showing the first {lines.length} lines.</span>}
              </div>

              <div className="trace-timeline" ref={timelineRef}>
              <svg
                width={width}
                height={trace.services.length * LANE_HEIGHT + 22}
                role="img"
                aria-label={`Timeline over ${trace.durationMs ?? 0} ms across ${trace.services.length} services`}
              >
                {trace.services.map((s, lane) => (
                  <g key={s} transform={`translate(0, ${lane * LANE_HEIGHT})`}>
                    <text x="0" y={LANE_HEIGHT / 2 + 4} className="trace-lane-label">{s}</text>
                    <line x1={LABEL_WIDTH} x2={LABEL_WIDTH + plot} y1={LANE_HEIGHT / 2} y2={LANE_HEIGHT / 2} className="trace-lane-line" />
                  </g>
                ))}
                {shown.map((l) => {
                  const lane = trace.services.indexOf(l.service);
                  const tone = levelTone(l.level);
                  return (
                      <circle
                        key={l.i}
                        cx={xAt(l.offset)}
                        cy={lane * LANE_HEIGHT + LANE_HEIGHT / 2}
                        r={focus === l.i ? 6 : 4}
                        className={`trace-dot ${tone ? `trace-dot--${tone}` : ''}`}
                        onMouseEnter={() => setFocus(l.i)}
                        onClick={() => jumpTo(l.i)}
                      >
                        <title>{`${offsetLabel(l.offset)} ${l.service}: ${l.message}`}</title>
                      </circle>
                  );
                })}
                <g transform={`translate(0, ${trace.services.length * LANE_HEIGHT + 14})`} className="trace-axis">
                  <text x={LABEL_WIDTH}>0</text>
                  <text x={LABEL_WIDTH + plot} textAnchor="end">{offsetLabel(trace.durationMs ?? 0)}</text>
                </g>
              </svg>
              </div>

              <div className="trace-lines-bar">
                <span className="field-label">Log lines</span>
                <label className="check-label">
                  <input type="checkbox" checked={ownOnly} onChange={(e) => setOwnOnly(e.target.checked)} />
                  SmartConnect lines only{hidden > 0 ? ` (${hidden} framework lines hidden)` : ''}
                </label>
              </div>
              <ol className="trace-lines">
                {shown.map((l) => {
                  const tone = levelTone(l.level);
                  return (
                    <li
                      key={l.i}
                      ref={(el) => (el ? rowRefs.current.set(l.i, el) : rowRefs.current.delete(l.i))}
                      className={`trace-line ${focus === l.i ? 'trace-line--focus' : ''}`}
                      onMouseEnter={() => setFocus(l.i)}
                    >
                      <span className="trace-line-offset">{offsetLabel(l.offset)}</span>
                      <span className="trace-line-main">
                        <span className="trace-line-meta">
                          <span className="trace-service">{l.service}</span>
                          {tone && (
                            <span className={`metric-flag tone-${tone}`}>
                              <AlertIcon />
                              {l.level.toLowerCase()}
                            </span>
                          )}
                          {l.logger && <span className="trace-line-logger">{l.logger}</span>}
                        </span>
                        <span className="trace-line-message">{l.message}</span>
                      </span>
                    </li>
                  );
                })}
              </ol>
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
