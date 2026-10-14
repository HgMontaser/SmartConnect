// The app's two loading indicators: PageSpinner covers the whole viewport (session check,
// app shell chunk), SectionSpinner fills just the area that's loading (a page, a table, a
// dialog). `overlay` centres it over a dimmed screen for lazily loaded dialogs.

export function PageSpinner({ label = 'Loading…' }) {
  return (
    <div className="centered-state" role="status" aria-live="polite">
      <div className="dot-spinner dot-spinner--lg" />
      <p className="centered-state-text">{label}</p>
    </div>
  );
}

export function SectionSpinner({ label, overlay = false }) {
  const spinner = (
    <div className="section-spinner" role="status" aria-live="polite">
      <div className="dot-spinner" />
      {label && <span className="section-spinner-text">{label}</span>}
    </div>
  );
  return overlay ? <div className="modal-overlay">{spinner}</div> : spinner;
}
