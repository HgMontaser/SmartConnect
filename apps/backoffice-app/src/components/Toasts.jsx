export default function Toasts({ toasts }) {
  return (
    <div className="toast-stack" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="toast">
          <span className="live-dot live-dot--success" />
          {t.message}
        </div>
      ))}
    </div>
  );
}
