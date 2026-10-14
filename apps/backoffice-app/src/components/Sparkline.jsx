import { useEffect, useRef, useState } from 'react';
import { formatClock, formatMs, formatRate } from '../utils/metrics.js';

const HEIGHT = 48;
const PAD_TOP = 6;

// Requests per minute over the range as a single accent area, with a crosshair tooltip that
// also shows that minute's 5xx count and p95. Width follows the card.
export default function Sparkline({ points, bucketSeconds, label }) {
  const wrapRef = useRef(null);
  const [width, setWidth] = useState(0);
  const [hover, setHover] = useState(null);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return undefined;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const perMinute = 60 / bucketSeconds;
  const values = points.map((p) => p.requests * perMinute);
  const peak = Math.max(...values, 0);
  const n = points.length;
  const x = (i) => (n <= 1 ? 0 : (i / (n - 1)) * width);
  const y = (v) => HEIGHT - (peak === 0 ? 0 : (v / peak) * (HEIGHT - PAD_TOP));

  const line = values.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join('');
  const area = n > 0 ? `${line}L${x(n - 1).toFixed(1)},${HEIGHT}L0,${HEIGHT}Z` : '';

  const onMove = (event) => {
    if (n === 0 || width === 0) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const i = Math.round(((event.clientX - rect.left) / width) * (n - 1));
    setHover(Math.min(n - 1, Math.max(0, i)));
  };

  const hovered = hover != null ? points[hover] : null;
  const summary = `${label}: peak ${formatRate(peak)} requests per minute over the range`;

  return (
    <div className="sparkline" ref={wrapRef}>
      <svg
        width={width}
        height={HEIGHT}
        role="img"
        aria-label={summary}
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        {width > 0 && (
          <>
            <path d={area} className="sparkline-area" />
            <path d={line} className="sparkline-line" />
            <line x1="0" x2={width} y1={HEIGHT - 0.5} y2={HEIGHT - 0.5} className="sparkline-base" />
            {hovered && (
              <>
                <line x1={x(hover)} x2={x(hover)} y1="0" y2={HEIGHT} className="sparkline-crosshair" />
                <circle cx={x(hover)} cy={y(values[hover])} r="4" className="sparkline-dot" />
              </>
            )}
          </>
        )}
      </svg>
      {hovered && (
        <div
          className={`sparkline-tooltip ${x(hover) > width / 2 ? 'sparkline-tooltip--left' : ''}`}
          style={{ left: x(hover) }}
        >
          <span className="sparkline-tooltip-time">{formatClock(hovered.t)}</span>
          <span><strong>{formatRate(values[hover])}</strong> req/min</span>
          <span><strong>{Math.round(hovered.errors)}</strong> 5xx</span>
          <span>p95 <strong>{formatMs(hovered.p95Ms)}</strong></span>
        </div>
      )}
      <div className="sparkline-axis">
        <span>{n > 0 ? formatClock(points[0].t) : ''}</span>
        <span>peak {formatRate(peak)}/min</span>
        <span>now</span>
      </div>
    </div>
  );
}
