import { useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';

export default function ConfirmDialog({ title, message, confirmLabel = 'Confirm', danger = false, onConfirm, onClose }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const handleConfirm = async () => {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      onClose();
    } catch (err) {
      setError(err.message || 'Something went wrong. Please try again.');
      setBusy(false);
    }
  };

  return (
    <Modal title={title} onClose={onClose}>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      <p className="confirm-message">{message}</p>
      <div className="modal-actions">
        <button type="button" className="ghost-button" onClick={onClose}>
          Cancel
        </button>
        <button
          type="button"
          className={danger ? 'danger-button' : 'primary-button'}
          onClick={handleConfirm}
          disabled={busy}
        >
          {busy ? 'Working…' : confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
