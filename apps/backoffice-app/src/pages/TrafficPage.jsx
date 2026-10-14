import { useCallback, useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import TrendChart from '../components/TrendChart.jsx';
import EndpointBreakdown from '../components/EndpointBreakdown.jsx';
import { AlertIcon } from '../components/icons.jsx';
import usePolling from '../hooks/usePolling.js';
import * as api from '../api.js';
import { errorLevel, formatClock, formatMs, formatPercent, formatRate } from '../utils/metrics.js';

const RANGES = [
  { id: '15m', label: '15 min' },
  { id: '1h', label: '1 hour' },
  { id: '6h', label: '6 hours' },
  { id: '24h', label: '24 hours' },
];

const GATEWAY = 'api-gateway';

// Per bucket: the three measures each row charts.
function measures(series, bucketSeconds) {
  const perMinute = 60 / bucketSeconds;
  return {
    rate: series.map((p) => p.requests * perMinute),
    errors: series.map((p) => (p.requests > 0 ? p.errors / p.requests : null)),
    p95: series.map((p) => p.p95Ms),
  };
}

function MetricCell({ title, value, caption, flag, children }) {
  return (
    <div className="traffic-cell">
      <div className="traffic-cell-head">
        <span className="metric-label">{title}</span>
        <span className="traffic-cell-value">
          {value}
          {flag && (
            <span className={`metric-flag tone-${flag.tone}`}>
              <AlertIcon />
              {flag.label}
            </span>
          )}
        </span>
        <span className="traffic-cell-caption">{caption}</span>
      </div>
      {children}
    </div>
  );
}

export default function TrafficPage({ getToken }) {
  const [range, setRange] = useState('1h');
  const [hover, setHover] = useState(null);
  const [open, setOpen] = useState(null);

  const load = useCallback(() => api.getPlatformMetrics(range, getToken()), [range, getToken]);
  const { data, error, setError, loading } = usePolling(load, 30000);

  // A range switch keeps showing the old data until the new one lands; never mix the two.
  const current = data?.range === range ? data : null;
  const services = [...(current?.services ?? [])].sort((a, b) =>
    a.service === GATEWAY ? -1 : b.service === GATEWAY ? 1 : a.service.localeCompare(b.service)
  );
  const edge = services.find((s) => s.service === GATEWAY);
  const hoverTime = hover != null && services[0]?.series[hover] ? formatClock(services[0].series[hover].t) : null;
  const caption = hoverTime ?? 'last 5 min';

  return (
    <>
      <PageHeader
        title="Traffic"
        subtitle="Requests, server errors and latency of every service, from the metrics they push every 30s. Hover any chart to read all of them at that moment; open a service to see its endpoints."
      />
      <div className="filter-bar traffic-toolbar">
        <div className="segmented" role="group" aria-label="Time range">
          {RANGES.map((r) => (
            <button
              key={r.id}
              type="button"
              className={`segmented-item ${range === r.id ? 'segmented-item--active' : ''}`}
              onClick={() => setRange(r.id)}
            >
              {r.label}
            </button>
          ))}
        </div>
        <span className="traffic-hover-time">{hoverTime ? `at ${hoverTime}` : 'hover a chart to compare'}</span>
      </div>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      {(loading || !current) && !error && <SectionSpinner label="Loading traffic…" />}
      {current && (
        <>
          <div className="stat-row">
            <div className="stat-tile">
              <span className="stat-value">{formatRate(edge?.requestsPerMinute)}</span>
              <span className="stat-label">req/min at the gateway</span>
            </div>
            <div className="stat-tile">
              <span className="stat-value">{formatPercent(edge?.errorRate)}</span>
              <span className="stat-label">5xx at the gateway</span>
            </div>
            <div className="stat-tile">
              <span className="stat-value">{formatMs(edge?.p95Ms)}</span>
              <span className="stat-label">p95 at the gateway</span>
            </div>
            <div className="stat-tile">
              <span className="stat-value">{services.length}</span>
              <span className="stat-label">services reporting</span>
            </div>
          </div>

          {services.length === 0 && <div className="table-card table-empty">No service has reported request metrics in this range.</div>}

          <div className="traffic-list">
            {services.map((s) => {
              const m = measures(s.series, current.bucketSeconds);
              const at = (arr) => (hover != null ? arr[hover] : undefined);
              const rate = hover != null ? at(m.rate) : s.requestsPerMinute;
              const errs = hover != null ? at(m.errors) : s.errorRate;
              const p95 = hover != null ? at(m.p95) : s.p95Ms;
              const isOpen = open === s.service;
              return (
                <section key={s.service} className="table-card traffic-row">
                  <button
                    type="button"
                    className="traffic-row-head"
                    aria-expanded={isOpen}
                    onClick={() => setOpen(isOpen ? null : s.service)}
                  >
                    <span className="mono-strong">{s.service}</span>
                    <span className="traffic-row-toggle">{isOpen ? 'Hide' : s.service === GATEWAY ? 'Routes' : 'Endpoints'}</span>
                  </button>
                  <div className="traffic-cells">
                    <MetricCell title="Requests" value={`${formatRate(rate)}/min`} caption={caption}>
                      <TrendChart
                        values={m.rate}
                        kind="area"
                        hoverIndex={hover}
                        onHover={setHover}
                        peakLabel={(peak) => `${formatRate(peak)}/min`}
                        label={`${s.service} requests per minute`}
                      />
                    </MetricCell>
                    <MetricCell title="5xx errors" value={formatPercent(errs)} caption={caption} flag={errorLevel(errs)}>
                      <TrendChart
                        values={m.errors}
                        hoverIndex={hover}
                        onHover={setHover}
                        peakLabel={(peak) => formatPercent(peak)}
                        label={`${s.service} share of requests ending in a 5xx`}
                      />
                    </MetricCell>
                    <MetricCell title="p95 latency" value={formatMs(p95)} caption={caption}>
                      <TrendChart
                        values={m.p95}
                        hoverIndex={hover}
                        onHover={setHover}
                        peakLabel={(peak) => formatMs(peak)}
                        label={`${s.service} p95 latency of its slowest endpoint`}
                      />
                    </MetricCell>
                  </div>
                  {isOpen && <EndpointBreakdown service={s.service} range={range} getToken={getToken} />}
                </section>
              );
            })}
          </div>
        </>
      )}
    </>
  );
}
