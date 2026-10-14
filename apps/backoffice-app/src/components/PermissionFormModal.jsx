import { useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';

export default function PermissionFormModal({ mode, permission, onClose, onCreate, onUpdate }) {
  const isEdit = mode === 'edit';
  const [name, setName] = useState(permission?.name || '');
  const [description, setDescription] = useState(permission?.description || '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (isEdit) await onUpdate(permission.name, { description });
      else await onCreate({ name: name.trim(), description });
      onClose();
    } catch (err) {
      setError(err.message || 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title={isEdit ? 'Edit permission' : 'New permission'} subtitle={isEdit ? permission.name : undefined} onClose={onClose}>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
      <form onSubmit={handleSubmit} className="modal-form">
        {!isEdit && (
          <div className="field">
            <label className="field-label" htmlFor="perm-name">
              Name
            </label>
            <input
              id="perm-name"
              className="text-input mono-input"
              value={name}
              onChange={(e) => setName(e.target.value.toLowerCase())}
              placeholder="resource:action, e.g. reports:export"
              pattern="[a-z][a-z0-9\-]{0,29}:[a-z][a-z0-9\-]{0,29}"
              title="resource:action - lowercase letters, digits, '-'"
              autoFocus
              required
            />
            <span className="field-hint">New permissions are granted to the admin role automatically.</span>
          </div>
        )}
        <div className="field">
          <label className="field-label" htmlFor="perm-description">
            Description
          </label>
          <input
            id="perm-description"
            className="text-input"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What this permission allows"
            maxLength={255}
          />
        </div>
        <div className="modal-actions">
          <button type="button" className="ghost-button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={submitting}>
            {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create permission'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
