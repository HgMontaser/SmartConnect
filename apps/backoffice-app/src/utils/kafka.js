// Formatting for the Events page.

export function formatBytes(n) {
  if (n == null || n < 0) return '—';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export function formatCount(n) {
  return n == null ? '—' : Math.round(n).toLocaleString();
}

// retention.ms -> "7 days" / "forever"
export function formatRetention(ms) {
  if (ms == null) return '—';
  const v = Number(ms);
  if (v < 0) return 'forever';
  const hours = v / 3_600_000;
  if (hours < 1) return `${Math.round(v / 60_000)} min`;
  if (hours < 48) return `${Math.round(hours)} h`;
  return `${Math.round(hours / 24)} days`;
}

// The envelope's occurredAt is ISO text or epoch seconds (Jackson's default for Instant).
export function parseOccurredAt(value) {
  if (value == null) return null;
  const n = Number(value);
  const date = Number.isFinite(n) ? new Date(n < 1e11 ? n * 1000 : n) : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(value) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString([], {
    year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }) + `.${String(date.getMilliseconds()).padStart(3, '0')}`;
}

export function isDeadLetterTopic(topic) {
  return /\.dlt$/.test(topic || '');
}

// Headers Spring Kafka adds when it parks a record on a dead-letter topic.
export const DLT_ERROR_HEADER = 'kafka_dlt-exception-message';
