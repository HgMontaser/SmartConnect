// Number formatting for request metrics: compact, never more precision than the reader needs.

export function formatRate(perMinute) {
  if (perMinute == null) return '—';
  if (perMinute >= 1000) return `${(perMinute / 1000).toFixed(perMinute >= 10000 ? 0 : 1)}k`;
  if (perMinute >= 10) return Math.round(perMinute).toString();
  if (perMinute > 0) return perMinute.toFixed(1);
  return '0';
}

export function formatPercent(share) {
  if (share == null) return '—';
  const pct = share * 100;
  if (pct === 0) return '0%';
  if (pct < 0.1) return '<0.1%';
  return `${pct < 10 ? pct.toFixed(1) : Math.round(pct)}%`;
}

export function formatMs(ms) {
  if (ms == null) return '—';
  if (ms >= 1000) return `${(ms / 1000).toFixed(ms >= 10000 ? 0 : 1)}s`;
  return `${Math.round(ms)}ms`;
}

export function formatClock(epochMs) {
  return new Date(epochMs).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
}

// 5xx share that deserves attention; status colors always come with a label.
export function errorLevel(share) {
  if (share == null) return null;
  if (share >= 0.05) return { tone: 'bad', label: 'high' };
  if (share >= 0.01) return { tone: 'warn', label: 'elevated' };
  return null;
}
