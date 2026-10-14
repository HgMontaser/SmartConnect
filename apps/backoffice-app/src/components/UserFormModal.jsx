import { useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';

export default function UserFormModal({ mode, user, roles, isSelf, onClose, onCreate, onUpdate }) {
  const isEdit = mode === 'edit';
  const [form, setForm] = useState(() => ({
    username: user?.username || '',
    email: user?.email || '',
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    password: '',
    enabled: user?.enabled ?? true,
    realmRoles: user ? user.realmRoles || [] : roles.some((r) => r.name === 'user') ? ['user'] : [],
  }));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const toggleRole = (role) => {
    setForm((prev) => {
      const has = prev.realmRoles.includes(role);
      const next = has ? prev.realmRoles.filter((r) => r !== role) : [...prev.realmRoles, role];
      return { ...prev, realmRoles: next };
    });
  };

  const handleChange = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (isEdit) {
        await onUpdate(user.id, {
          email: form.email,
          firstName: form.firstName,
          lastName: form.lastName,
          enabled: form.enabled,
          realmRoles: form.realmRoles,
        });
      } else {
        await onCreate({
          username: form.username,
          email: form.email,
          firstName: form.firstName,
          lastName: form.lastName,
          password: form.password,
          realmRoles: form.realmRoles,
        });
      }
      onClose();
    } catch (err) {
      setError(err.message || 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title={isEdit ? 'Edit user' : 'New user'} subtitle={isEdit ? user.username : undefined} onClose={onClose}>
      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

      <form onSubmit={handleSubmit} className="modal-form">
        {!isEdit && (
          <div className="field">
            <label className="field-label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              className="text-input"
              value={form.username}
              onChange={handleChange('username')}
              autoComplete="off"
              required
            />
          </div>
        )}

        <div className="field">
          <label className="field-label" htmlFor="email">
            Email
          </label>
          <input id="email" type="email" className="text-input" value={form.email} onChange={handleChange('email')} required />
        </div>

        <div className="field-row">
          <div className="field">
            <label className="field-label" htmlFor="firstName">
              First name
            </label>
            <input id="firstName" className="text-input" value={form.firstName} onChange={handleChange('firstName')} />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="lastName">
              Last name
            </label>
            <input id="lastName" className="text-input" value={form.lastName} onChange={handleChange('lastName')} />
          </div>
        </div>

        {!isEdit && (
          <div className="field">
            <label className="field-label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              className="text-input"
              value={form.password}
              onChange={handleChange('password')}
              autoComplete="new-password"
              minLength={8}
              required
            />
            <span className="field-hint">At least 8 characters.</span>
          </div>
        )}

        <div className="field">
          <span className="field-label">Roles</span>
          {roles.length === 0 ? (
            <span className="field-hint">No roles available.</span>
          ) : (
            <div className="checkbox-row">
              {roles.map((role) => (
                <label key={role.name} className="checkbox-pill" title={role.description || undefined}>
                  <input
                    type="checkbox"
                    checked={form.realmRoles.includes(role.name)}
                    onChange={() => toggleRole(role.name)}
                  />
                  <span>{role.name}</span>
                </label>
              ))}
            </div>
          )}
        </div>

        {isEdit && (
          <label className={`switch-row ${isSelf ? 'switch-row--disabled' : ''}`}>
            <span>
              <span className="switch-label">Account active</span>
              <span className="field-hint">
                {isSelf ? "You can't suspend your own account." : 'Suspended users cannot sign in.'}
              </span>
            </span>
            <input
              type="checkbox"
              className="switch"
              checked={form.enabled}
              disabled={isSelf}
              onChange={(e) => setForm((prev) => ({ ...prev, enabled: e.target.checked }))}
            />
          </label>
        )}

        <div className="modal-actions">
          <button type="button" className="ghost-button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary-button" disabled={submitting}>
            {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create user'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
