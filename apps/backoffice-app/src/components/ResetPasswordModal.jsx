import { useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';

export default function ResetPasswordModal({ user, onClose, onReset }) {
  const [password, setPassword] = useState('');
  const [temporary, setTemporary] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onReset(user, { password, temporary });
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to reset password.');
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Reset password" subtitle={user.username} onClose={onClose}>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      <form onSubmit={handleSubmit} className="modal-form">
        <div className="field">
          <label className="field-label" htmlFor="new-password">
            New password
          </label>
          <input
            id="new-password"
            type="password"
            className="text-input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
            autoFocus
          />
          <span className="field-hint">At least 8 characters.</span>
        </div>
        <label className="switch-row">
          <span>
            <span className="switch-label">Require change at next sign-in</span>
            <span className="field-hint">The user picks their own password after signing in with this one.</span>
          </span>
          <input type="checkbox" className="switch" checked={temporary} onChange={(e) => setTemporary(e.target.checked)} />
        </label>
        <div className="modal-actions">
          <button type="button" className="ghost-button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={submitting}>
            {submitting ? 'Saving…' : 'Reset password'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
