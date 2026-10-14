import { useCallback } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import usePolling from '../hooks/usePolling.js';
import * as api from '../api.js';

const CIRCUIT_LABELS = {
  CLOSED: { label: 'Closed', tone: 'ok', hint: 'Healthy - calls flow normally' },
  HALF_OPEN: { label: 'Half-open', tone: 'warn', hint: 'Testing recovery with a few calls' },
  OPEN: { label: 'Open', tone: 'bad', hint: 'Failing - calls are refused for a while' },
  FORCED_OPEN: { label: 'Forced open', tone: 'bad', hint: 'Manually opened' },
  DISABLED: { label: 'Disabled', tone: 'muted', hint: 'Breaker disabled' },
};

function instanceTone(status) {
  if (status === 'PASSING') return 'ok';
  if (status === 'WARNING') return 'warn';
  return 'bad';
}

export default function GatewayHealthPage({ getToken }) {
  const load = useCallback(() => api.getPlatformServices(getToken()), [getToken]);
  const { data: services, error, setError, loading } = usePolling(load, 5000);

  const total = services?.length ?? 0;
  const healthy = services?.filter((s) => s.instances.length > 0 && s.instances.every((i) => i.status === 'PASSING')).length ?? 0;

  return (
    <>
      <PageHeader
        title="Gateway health"
        subtitle="Services registered in Consul, their instances, the routes the gateway built for them, and their circuit breakers. Refreshes every 5s."
      />
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      {loading && !services && <SectionSpinner label="Loading platform state…" />}
      {services && (
        <>
          <div className="stat-row">
            <div className="stat-tile">
              <span className="stat-value">{total}</span>
              <span className="stat-label">services</span>
            </div>
            <div className="stat-tile">
              <span className={`stat-value ${healthy === total ? 'tone-ok' : 'tone-warn'}`}>{healthy}/{total}</span>
              <span className="stat-label">fully healthy</span>
            </div>
            <div className="stat-tile">
              <span className="stat-value">{services.reduce((n, s) => n + s.routes.length, 0)}</span>
              <span className="stat-label">gateway routes</span>
            </div>
          </div>
          <div className="service-grid">
            {services.map((s) => {
              const circuit = s.circuit ? CIRCUIT_LABELS[s.circuit] || { label: s.circuit, tone: 'muted' } : null;
              return (
                <section key={s.name} className="table-card service-card">
                  <div className="service-card-head">
                    <span className="mono-strong">{s.name}</span>
                    {circuit ? (
                      <span className={`state-pill tone-${circuit.tone}`} title={circuit.hint}>
                        circuit {circuit.label.toLowerCase()}
                      </span>
                    ) : (
                      s.routes.some((r) => r.scheme === 'http') && (
                        <span className="state-pill tone-muted" title="The breaker is created on the first request">
                          no traffic yet
                        </span>
                      )
                    )}
                  </div>
                  <div className="service-card-body">
                    <span className="field-label">Instances</span>
                    {s.instances.length === 0 && <span className="muted-text">None registered</span>}
                    {s.instances.map((i) => (
                      <div key={i.id} className="instance-row">
                        <span className={`dot tone-bg-${instanceTone(i.status)}`} />
                        <span className="mono-cell">{i.address}:{i.port}</span>
                        <span className={`instance-status tone-${instanceTone(i.status)}`}>{i.status.toLowerCase()}</span>
                      </div>
                    ))}
                    <span className="field-label">Routes</span>
                    {s.routes.length === 0 ? (
                      <span className="muted-text">Not routed (no smartconnect.gateway.routes)</span>
                    ) : (
                      <div className="perm-chip-group">
                        {s.routes.flatMap((r) =>
                          r.paths.map((p) => (
                            <span key={`${r.id}-${p}`} className="perm-chip">
                              {r.scheme === 'ws' ? 'ws ' : ''}
                              {p}
                            </span>
                          ))
                        )}
                      </div>
                    )}
                    {s.timeout && <span className="muted-text">Response timeout {s.timeout}</span>}
                  </div>
                </section>
              );
            })}
          </div>
        </>
      )}
    </>
  );
}
