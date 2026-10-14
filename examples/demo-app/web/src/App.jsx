import { useCallback, useEffect, useRef, useState } from 'react';
import keycloak from './keycloak.js';
import { call } from './api.js';

export default function App() {
  const [status, setStatus] = useState('checking');
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    keycloak
      .init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
      })
      .then((authenticated) => setStatus(authenticated ? 'in' : 'out'))
      .catch(() => setStatus('error'));
  }, []);

  if (status === 'checking') return <Centered><div className="spinner" /> Checking your session…</Centered>;
  if (status === 'error') return <Centered>Could not reach Keycloak.</Centered>;
  if (status === 'out') {
    return (
      <Centered>
        <h1>SmartConnect demo app</h1>
        <p className="muted">A mock business app plugged into the platform. Sign in with your SmartConnect account.</p>
        <button className="primary" onClick={() => keycloak.login()}>Sign in</button>
      </Centered>
    );
  }
  return <Demo />;
}

function Centered({ children }) {
  return <div className="centered">{children}</div>;
}

function Demo() {
  const [me, setMe] = useState(null);
  const [meError, setMeError] = useState(null);

  useEffect(() => {
    call(keycloak, '/api/demo/whoami').then(setMe).catch(setMeError);
  }, []);

  const can = (p) => me?.permissions?.includes(p);

  return (
    <div className="page">
      <header className="header">
        <div>
          <span className="wordmark">SmartConnect</span> <span className="muted">demo app</span>
        </div>
        <div className="header-right">
          <span className="mono">{keycloak.tokenParsed?.preferred_username}</span>
          <button className="ghost" onClick={() => keycloak.logout({ redirectUri: window.location.origin })}>Sign out</button>
        </div>
      </header>

      {meError && (
        <div className="card warn">
          {meError.status === 403
            ? 'You are signed in but lack demo:read. In the backoffice, add demo:read to one of your roles, then sign out and back in.'
            : meError.message}
        </div>
      )}

      {me && (
        <>
          <section className="card">
            <h2>Who demo-service sees</h2>
            <p className="muted">Permissions from your token, as checked by demo-service. demo:* came from its own catalog.</p>
            <div className="chips">
              {me.permissions.map((p) => (
                <span key={p} className={`chip ${p.startsWith('demo:') ? 'chip--accent' : ''}`}>{p}</span>
              ))}
            </div>
          </section>
          <Connectivity />
          <Notes canWrite={can('demo:write')} />
        </>
      )}
    </div>
  );
}

function Connectivity() {
  const [checks, setChecks] = useState(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState(null);

  const run = useCallback(() => {
    setRunning(true);
    setError(null);
    call(keycloak, '/api/demo/connectivity')
      .then(setChecks)
      .catch((e) => setError(e.message))
      .finally(() => setRunning(false));
  }, []);

  useEffect(run, [run]);

  return (
    <section className="card">
      <div className="card-head">
        <h2>Connectivity</h2>
        <button className="ghost" onClick={run} disabled={running}>{running ? 'Running…' : 'Run again'}</button>
      </div>
      <p className="muted">browser → gateway → demo-service (via Consul) → user-service (via Consul), with both token modes.</p>
      {error && <p className="error">{error}</p>}
      {checks && (
        <table>
          <tbody>
            {checks.map((c) => (
              <tr key={c.name}>
                <td><span className={`dot ${c.ok ? 'dot--ok' : 'dot--bad'}`} /></td>
                <td>{c.name}</td>
                <td className="mono">{c.status || '—'}</td>
                <td className="muted">{c.detail}</td>
                <td className="mono muted">{c.latencyMs} ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Notes({ canWrite }) {
  const [notes, setNotes] = useState([]);
  const [text, setText] = useState('');
  const [error, setError] = useState(null);

  const load = useCallback(() => call(keycloak, '/api/demo/notes').then(setNotes).catch((e) => setError(e.message)), []);
  useEffect(() => {
    load();
  }, [load]);

  const add = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await call(keycloak, '/api/demo/notes', { method: 'POST', body: { text } });
      setText('');
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <section className="card">
      <h2>Notes</h2>
      <p className="muted">Mock data. Reading needs demo:read, adding needs demo:write.</p>
      {canWrite ? (
        <form onSubmit={add} className="note-form">
          <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Write a note" maxLength={280} required />
          <button className="primary">Add</button>
        </form>
      ) : (
        <p className="muted">You can read notes; add demo:write to a role of yours to write them.</p>
      )}
      {error && <p className="error">{error}</p>}
      <ul className="notes">
        {notes.length === 0 && <li className="muted">No notes yet.</li>}
        {notes.map((n) => (
          <li key={n.id}>
            <span>{n.text}</span>
            <span className="mono muted">{n.author}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
