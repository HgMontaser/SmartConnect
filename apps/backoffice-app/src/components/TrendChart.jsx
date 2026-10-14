import { useEffect, useRef, useState } from 'react';

const PAD_TOP = 8;

// One series over time in the accent - area for volumes, line for rates and latencies. Gaps
// (null values: no traffic) break the line instead of drawing as zero. The hover position is
// owned by the caller, so every chart on a page can share one crosshair.
export default function TrendChart({ values, kind = 'line', height = 64, hoverIndex, onHover, peakLabel, label }) {
  const wrapRef = useRef(null);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return undefined;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const n = values.length;
  const peak = Math.max(0, ...values.filter((v) => v != null));
  const x = (i) => (n <= 1 ? 0 : (i / (n - 1)) * width);
  const y = (v) => height - (peak === 0 ? 0 : (v / peak) * (height - PAD_TOP));

  // Split into runs of non-null values so a gap stays a gap.
  const runs = [];
  let run = [];
  values.forEach((v, i) => {
    if (v == null) {
      if (run.length) runs.push(run);
      run = [];
    } else {
      run.push(i);
    }
  });
  if (run.length) runs.push(run);

  const linePath = (r) => r.map((i, k) => `${k === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(values[i]).toFixed(1)}`).join('');
  const areaPath = (r) => `${linePath(r)}L${x(r[r.length - 1]).toFixed(1)},${height}L${x(r[0]).toFixed(1)},${height}Z`;

  const onMove = (event) => {
    if (n === 0 || width === 0) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const i = Math.round(((event.clientX - rect.left) / width) * (n - 1));
    onHover(Math.min(n - 1, Math.max(0, i)));
  };

  const hoveredValue = hoverIndex != null ? values[hoverIndex] : null;

  return (
    <div className="trend-chart" ref={wrapRef}>
      {peakLabel && <span className="trend-chart-peak">{peakLabel(peak)}</span>}
      <svg
        width={width}
        height={height}
        role="img"
        aria-label={label}
        onMouseMove={onMove}
        onMouseLeave={() => onHover(null)}
      >
        {width > 0 && (
          <>
            <line x1="0" x2={width} y1={PAD_TOP - 0.5} y2={PAD_TOP - 0.5} className="trend-chart-grid" />
            {kind === 'area' && runs.map((r) => <path key={`a${r[0]}`} d={areaPath(r)} className="sparkline-area" />)}
            {runs.map((r) =>
              r.length === 1 ? (
                <circle key={`p${r[0]}`} cx={x(r[0])} cy={y(values[r[0]])} r="2" className="trend-chart-point" />
              ) : (
                <path key={`l${r[0]}`} d={linePath(r)} className="sparkline-line" />
              )
            )}
            <line x1="0" x2={width} y1={height - 0.5} y2={height - 0.5} className="sparkline-base" />
            {hoverIndex != null && (
              <>
                <line x1={x(hoverIndex)} x2={x(hoverIndex)} y1="0" y2={height} className="sparkline-crosshair" />
                {hoveredValue != null && <circle cx={x(hoverIndex)} cy={y(hoveredValue)} r="4" className="sparkline-dot" />}
              </>
            )}
          </>
        )}
      </svg>
    </div>
  );
}
