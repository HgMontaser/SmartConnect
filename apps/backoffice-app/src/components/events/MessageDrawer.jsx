import { useCallback, useEffect, useState } from 'react';
import { SectionSpinner } from '../Spinner.jsx';
import { AlertIcon, CloseIcon } from '../icons.jsx';
import JsonView, { prettyJson } from './JsonView.jsx';
import usePolling from '../../hooks/usePolling.js';
import * as api from '../../api.js';
import { DLT_ERROR_HEADER, formatBytes, formatDateTime, isDeadLetterTopic, parseOccurredAt } from '../../utils/kafka.js';

function Field({ label, children, mono = true }) {
  return (
    <div className="msg-field">
      <span className="msg-field-label">{label}</span>
      <span className={mono ? 'msg-field-value mono-cell' : 'msg-field-value'}>{children ?? '—'}</span>
    </div>
  );
}

// Everything about one Kafka record: where it sits, the SmartConnect event it carries, its
// headers and its payload. `position` is {topic, partition, offset}; the record is fetched.
export default function MessageDrawer({ position, getToken, canTrace, onTrace, onClose }) {
  const { topic, partition, offset } = position;
  const load = useCallback(() => api.getTopicMessage(topic, partition, offset, getToken()), [topic, partition, offset, getToken]);
  const { data: message, error, loading } = usePolling(load, 0);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(prettyJson(message.value).text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  };

  const envelope = message?.envelope;
  const dltError = message?.headers.find((h) => h.key === DLT_ERROR_HEADER)?.value;
  const occurred = parseOccurredAt(envelope?.occurredAt);

  return (
    <div className="drawer-overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="Kafka message">
        <header className="drawer-header">
          <div className="drawer-title">
            <h2 className="modal-heading">{envelope?.type || 'Message'}</h2>
            <span className="mono-cell drawer-id">
              {topic} · partition {partition} · offset {offset}
            </span>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
            <CloseIcon />
          </button>
        </header>
        <div className="drawer-body">
          {error && <div className="info-note">Couldn&apos;t load the message: {error}</div>}
          {loading && !message && <SectionSpinner label="Reading the record…" />}
          {!loading && !error && !message && (
            <p className="muted-text">This record is gone - Kafka&apos;s retention has removed it.</p>
          )}
          {message && (
            <>
              {(isDeadLetterTopic(topic) || dltError) && (
                <div className="dlt-banner" role="note">
                  <span className="metric-flag tone-bad">
                    <AlertIcon />
                    dead letter
                  </span>
                  <span>{dltError || 'Parked after its consumer gave up on it.'}</span>
                </div>
              )}

              <section className="msg-section">
                <h3 className="msg-section-title">Record</h3>
                <div className="msg-grid">
                  <Field label="Timestamp">{formatDateTime(message.timestamp)}</Field>
                  <Field label="Partition · offset">{`${message.partition} · ${message.offset}`}</Field>
                  <Field label="Key">{message.key}</Field>
                  <Field label="Size">{formatBytes(message.size)}</Field>
                </div>
              </section>

              {envelope && (
                <section className="msg-section">
                  <div className="msg-section-head">
                    <h3 className="msg-section-title">Event</h3>
                    {canTrace && envelope.correlationId && (
                      <button type="button" className="trace-button" onClick={() => onTrace(envelope.correlationId)}>
                        Trace the request
                      </button>
                    )}
                  </div>
                  <div className="msg-grid">
                    <Field label="Type">{envelope.type}</Field>
                    <Field label="Version">{envelope.version}</Field>
                    <Field label="Source">{envelope.source}</Field>
                    <Field label="Occurred">{occurred ? formatDateTime(occurred) : envelope.occurredAt}</Field>
                    <Field label="Event id">{envelope.id}</Field>
                    <Field label="Correlation id">{envelope.correlationId}</Field>
                  </div>
                </section>
              )}

              <section className="msg-section">
                <h3 className="msg-section-title">Headers <span className="field-label-count">{message.headers.length}</span></h3>
                {message.headers.length === 0 ? (
                  <span className="muted-text">No headers.</span>
                ) : (
                  <table className="data-table headers-table">
                    <tbody>
                      {message.headers.map((h, i) => (
                        <tr key={`${h.key}-${i}`}>
                          <td className="mono-cell">{h.key}</td>
                          <td className="mono-cell headers-value">
                            {h.value ?? '—'}
                            {h.binary && <span className="cell-sub">base64</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </section>

              <section className="msg-section">
                <div className="msg-section-head">
                  <h3 className="msg-section-title">
                    Payload
                    {message.binary && <span className="cell-sub">binary, shown as base64</span>}
                    {message.truncated && <span className="cell-sub">first 64 KB</span>}
                  </h3>
                  {message.value != null && (
                    <button type="button" className="trace-button" onClick={copy}>
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                  )}
                </div>
                {message.value == null ? <span className="muted-text">Empty (tombstone).</span> : <JsonView value={message.value} />}
              </section>
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
