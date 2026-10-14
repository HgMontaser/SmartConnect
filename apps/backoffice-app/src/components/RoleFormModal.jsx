import { useEffect, useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import PermissionPicker from './PermissionPicker.jsx';
import { SectionSpinner } from './Spinner.jsx';

export default function RoleFormModal({ mode, role, permissions, canWrite, loadMembers, onClose, onCreate, onUpdate }) {
  const isEdit = mode === 'edit';
  const isAdmin = role?.name === 'admin';
  const readOnly = !canWrite;
  const [form, setForm] = useState(() => ({
    name: role?.name || '',
    description: role?.description || '',
    permissions: role?.permissions || [],
  }));
  const [members, setMembers] = useState(null);
  const [membersError, setMembersError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!isEdit || !loadMembers) return;
    let cancelled = false;
    loadMembers(role.name)
      .then((data) => !cancelled && setMembers(data || []))
      .catch((err) => !cancelled && setMembersError(err.message || 'Failed to load members.'));
    return () => {
      cancelled = true;
    };
  }, [isEdit, loadMembers, role]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (isEdit) {
        await onUpdate(role.name, {
          description: form.description,
          permissions: isAdmin ? undefined : form.permissions,
        });
      } else {
        await onCreate({ name: form.name.trim(), description: form.description, permissions: form.permissions });
      }
      onClose();
    } catch (err) {
      setError(err.message || 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const title = readOnly ? 'Role' : isEdit ? 'Edit role' : 'New role';

  return (
    <Modal title={title} subtitle={isEdit ? role.name : undefined} onClose={onClose} wide>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

      <form onSubmit={handleSubmit} className="modal-form">
        {!isEdit && (
          <div className="field">
            <label className="field-label" htmlFor="role-name">
              Name
            </label>
            <input
              id="role-name"
              className="text-input mono-input"
              value={form.name}
              onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value.toLowerCase() }))}
              placeholder="e.g. support-agent"
              pattern="[a-z][a-z0-9_\-]{1,39}"
              title="2-40 chars: lowercase letters, digits, '-' or '_', starting with a letter"
              autoFocus
              required
            />
            <span className="field-hint">Lowercase letters, digits, “-” or “_”. Can't be changed later.</span>
          </div>
        )}

        <div className="field">
          <label className="field-label" htmlFor="role-description">
            Description
          </label>
          <input
            id="role-description"
            className="text-input"
            value={form.description}
            onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            placeholder="What this role is for"
            maxLength={255}
            disabled={readOnly}
          />
        </div>

        <div className="field">
          <span className="field-label">
            Permissions <span className="field-label-count">{form.permissions.length} selected</span>
          </span>
          {isAdmin && (
            <span className="info-note">The admin role always holds every permission, including ones added later.</span>
          )}
          <PermissionPicker
            permissions={permissions}
            selected={form.permissions}
            onChange={(next) => setForm((prev) => ({ ...prev, permissions: next }))}
            disabled={isAdmin || readOnly}
          />
        </div>

        {isEdit && loadMembers && (
          <div className="field">
            <span className="field-label">
              Members {members && <span className="field-label-count">{members.length}</span>}
            </span>
            {membersError && <span className="field-hint field-hint--error">{membersError}</span>}
            {!members && !membersError && <SectionSpinner label="Loading members…" />}
            {members && members.length === 0 && <span className="field-hint">No users have this role yet.</span>}
            {members && members.length > 0 && (
              <div className="member-list">
                {members.map((m) => (
                  <span key={m.id} className="member-chip" title={m.email || undefined}>
                    {m.username}
                  </span>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="modal-actions">
          <button type="button" className="ghost-button" onClick={onClose}>
            {readOnly ? 'Close' : 'Cancel'}
          </button>
          {!readOnly && (
            <button type="submit" className="primary-button" disabled={submitting}>
              {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create role'}
            </button>
          )}
        </div>
      </form>
    </Modal>
  );
}
