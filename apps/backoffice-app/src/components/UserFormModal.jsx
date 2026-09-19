import { useState } from 'react';
import ErrorBanner from './ErrorBanner.jsx';
import { CloseIcon } from './icons.jsx';

const ROLE_OPTIONS = ['admin', 'user'];

export default function UserFormModal({ mode, user, onClose, onCreate, onUpdate }) {
  const isEdit = mode === 'edit';
  const [form, setForm] = useState(() => ({
    username: user?.username || '',
    email: user?.email || '',
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    password: '',
    realmRoles: user?.realmRoles && user.realmRoles.length > 0 ? user.realmRoles : ['user'],
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

  const handleOverlayMouseDown = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

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
          realmRoles: form.realmRoles,
        });
      } else {
        await onCreate({
          username: form.username,
          email: form.email,
          firstName: form.firstName,
          lastName: form.lastName,
          password: form.password,
          realmRoles: form.realmRoles.length > 0 ? form.realmRoles : ['user'],
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
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className="modal-card">
        <div className="modal-header">
          <h2 className="modal-heading">{isEdit ? 'Edit user' : 'New user'}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
            <CloseIcon />
          </button>
        </div>

        {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

        <form onSubmit={handleSubmit} className="modal-form">
          <div className="field">
            <label className="field-label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              className="text-input"
              value={form.username}
              onChange={handleChange('username')}
              disabled={isEdit}
              required
            />
          </div>

          <div className="field">
            <label className="field-label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              type="email"
              className="text-input"
              value={form.email}
              onChange={handleChange('email')}
              required
            />
          </div>

          <div className="field-row">
            <div className="field">
              <label className="field-label" htmlFor="firstName">
                First name
              </label>
              <input
                id="firstName"
                className="text-input"
                value={form.firstName}
                onChange={handleChange('firstName')}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="lastName">
                Last name
              </label>
              <input
                id="lastName"
                className="text-input"
                value={form.lastName}
                onChange={handleChange('lastName')}
              />
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
                required
              />
            </div>
          )}

          <div className="field">
            <span className="field-label">Realm roles</span>
            <div className="checkbox-row">
              {ROLE_OPTIONS.map((role) => (
                <label key={role} className="checkbox-pill">
                  <input
                    type="checkbox"
                    checked={form.realmRoles.includes(role)}
                    onChange={() => toggleRole(role)}
                  />
                  <span>{role}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="modal-actions">
            <button type="button" className="ghost-button" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={submitting}>
              {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create user'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
