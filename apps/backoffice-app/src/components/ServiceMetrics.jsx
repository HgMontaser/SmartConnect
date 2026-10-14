import Sparkline from './Sparkline.jsx';
import { AlertIcon } from './icons.jsx';
import { errorLevel, formatMs, formatPercent, formatRate } from '../utils/metrics.js';

// The "how is it doing" strip of a Gateway health card: last-5-minute numbers and the trend.
export default function ServiceMetrics({ metrics, bucketSeconds }) {
  if (!metrics) {
    return <span className="muted-text">No request metrics reported</span>;
  }
  const level = errorLevel(metrics.errorRate);
  return (
    <div className="service-metrics">
      <div className="metric-trio">
        <div className="metric">
          <span className="metric-value">{formatRate(metrics.requestsPerMinute)}</span>
          <span className="metric-label">req/min</span>
        </div>
        <div className="metric">
          <span className="metric-value">
            {formatPercent(metrics.errorRate)}
            {level && (
              <span className={`metric-flag tone-${level.tone}`} title="Share of requests that ended in a 5xx">
                <AlertIcon />
                {level.label}
              </span>
            )}
          </span>
          <span className="metric-label">5xx errors</span>
        </div>
        <div className="metric">
          <span className="metric-value">{formatMs(metrics.p95Ms)}</span>
          <span className="metric-label" title="95th percentile latency of the slowest endpoint">p95 latency</span>
        </div>
      </div>
      <Sparkline points={metrics.series} bucketSeconds={bucketSeconds} label={metrics.service} />
    </div>
  );
}
