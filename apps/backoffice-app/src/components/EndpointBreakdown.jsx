import { useCallback, useState } from "react";
import { SectionSpinner } from "./Spinner.jsx";
import usePolling from "../hooks/usePolling.js";
import * as api from "../api.js";
import { formatMs, formatPercent } from "../utils/metrics.js";

const SORTS = [
  { id: "busiest", label: "Busiest", by: (r) => r.requests },
  { id: "slowest", label: "Slowest", by: (r) => r.p95Ms ?? -1 },
  { id: "errors", label: "Most 5xx", by: (r) => r.errors },
];

// "discovered-user-service" -> "user-service", "discovered-notification-service-ws" -> "notification-service (ws)"
function routeLabel(id) {
  const name = id.replace(/^discovered-/, "");
  return name.endsWith("-ws") ? `${name.slice(0, -3)} (ws)` : name;
}

function count(n) {
  return Math.round(n).toLocaleString();
}

// What one service's traffic is made of over the range: per endpoint, or per route for the
// gateway (with the requests it rate-limited itself).
export default function EndpointBreakdown({ service, range, getToken }) {
  const [sort, setSort] = useState("busiest");
  const load = useCallback(
    () => api.getEndpointMetrics(service, range, getToken()),
    [service, range, getToken],
  );
  const { data, error, loading } = usePolling(load, 30000);

  if (error)
    return (
      <div className="traffic-breakdown muted-text">
        Couldn't load the breakdown: {error}
      </div>
    );
  if (loading || !data || data.range !== range)
    return <SectionSpinner label="Loading endpoints…" />;

  const routes = data.grouping === "route";
  const rows = [...data.rows].sort(
    (a, b) =>
      SORTS.find((s) => s.id === sort).by(b) -
      SORTS.find((s) => s.id === sort).by(a),
  );
  const busiest = Math.max(1, ...rows.map((r) => r.requests));

  return (
    <div className="traffic-breakdown">
      <div className="traffic-breakdown-bar">
        <span className="field-label">
          {routes ? "Per route" : "Per endpoint"} · {RANGE_TEXT[range]}
        </span>
        <div
          className="segmented segmented--small"
          role="group"
          aria-label="Sort"
        >
          {SORTS.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`segmented-item ${sort === s.id ? "segmented-item--active" : ""}`}
              onClick={() => setSort(s.id)}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>
      {rows.length === 0 ? (
        <span className="muted-text">No requests in this range.</span>
      ) : (
        <div className="table-scroll">
          <table className="data-table breakdown-table">
            <thead>
              <tr>
                <th>{routes ? "Route" : "Endpoint"}</th>
                <th className="num">Requests</th>
                <th className="num">5xx</th>
                {routes && (
                  <th
                    className="num"
                    title="Answered 429 by the gateway's rate limiter"
                  >
                    Rate-limited
                  </th>
                )}
                <th className="num" title="The worst p95 seen in the range">
                  Worst p95
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={`${r.method ?? ""} ${r.path}`}>
                  <td>
                    <span className="endpoint-cell">
                      {r.method && (
                        <span className="method-tag">{r.method}</span>
                      )}
                      <span className="mono-cell">
                        {routes ? routeLabel(r.path) : r.path}
                      </span>
                    </span>
                  </td>
                  <td className="num">
                    <span className="volume-cell">
                      <span className="volume-track">
                        <span
                          className="volume-bar"
                          style={{ width: `${(r.requests / busiest) * 100}%` }}
                        />
                      </span>
                      <span>{count(r.requests)}</span>
                    </span>
                  </td>
                  <td className="num">
                    {count(r.errors)}
                    {r.errors > 0 && (
                      <span className="cell-sub">
                        {formatPercent(r.errorRate)}
                      </span>
                    )}
                  </td>
                  {routes && (
                    <td className="num">
                      {count(r.rateLimited)}
                      {r.rateLimited > 0 && (
                        <span className="cell-sub">
                          {formatPercent(r.rateLimited / r.requests)}
                        </span>
                      )}
                    </td>
                  )}
                  <td className="num">{formatMs(r.p95Ms)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

const RANGE_TEXT = {
  "15m": "last 15 minutes",
  "1h": "last hour",
  "6h": "last 6 hours",
  "24h": "last 24 hours",
};
