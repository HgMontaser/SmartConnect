import { lazy, Suspense, useMemo, useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import UsersTable from '../components/UsersTable.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import { P } from '../permissions.js';

// Dialogs are only downloaded the first time one is opened.
const UserFormModal = lazy(() => import('../components/UserFormModal.jsx'));
const ResetPasswordModal = lazy(() => import('../components/ResetPasswordModal.jsx'));
const ConfirmDialog = lazy(() => import('../components/ConfirmDialog.jsx'));

const STATUS_FILTERS = [
  { id: 'all', label: 'All' },
  { id: 'active', label: 'Active' },
  { id: 'suspended', label: 'Suspended' },
];

export default function UsersPage({ usersState, roles, can, currentUserId, notify }) {
  const { users, loading, error, setError, create, update, remove, resetPassword } = usersState;
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [roleFilter, setRoleFilter] = useState('');
  const [dialog, setDialog] = useState(null);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return users.filter((u) => {
      if (status === 'active' && !u.enabled) return false;
      if (status === 'suspended' && u.enabled) return false;
      if (roleFilter && !(u.realmRoles || []).includes(roleFilter)) return false;
      if (!q) return true;
      return [u.username, u.email, u.firstName, u.lastName].some((f) => (f || '').toLowerCase().includes(q));
    });
  }, [users, search, status, roleFilter]);

  const counts = useMemo(
    () => ({ all: users.length, active: users.filter((u) => u.enabled).length, suspended: users.filter((u) => !u.enabled).length }),
    [users]
  );

  const close = () => setDialog(null);

  const toggleEnabled = (user) =>
    setDialog({
      kind: 'confirm',
      title: user.enabled ? 'Suspend user' : 'Reactivate user',
      message: user.enabled
        ? `${user.username} won't be able to sign in until reactivated.`
        : `${user.username} will be able to sign in again.`,
      confirmLabel: user.enabled ? 'Suspend' : 'Reactivate',
      danger: user.enabled,
      onConfirm: async () => {
        await update(user.id, {
          email: user.email,
          firstName: user.firstName,
          lastName: user.lastName,
          enabled: !user.enabled,
        });
        notify(`${user.username} ${user.enabled ? 'suspended' : 'reactivated'}`);
      },
    });

  const confirmDelete = (user) =>
    setDialog({
      kind: 'confirm',
      title: 'Delete user',
      message: `Delete ${user.username}? This permanently removes the account and cannot be undone.`,
      confirmLabel: 'Delete user',
      danger: true,
      onConfirm: async () => {
        await remove(user.id);
        notify(`${user.username} deleted`);
      },
    });

  return (
    <>
      <PageHeader
        title="Users"
        subtitle="Accounts in the smartconnect realm, their roles and status."
        search={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search by name, username or email"
        action={
          can(P.USERS_WRITE) && (
            <button type="button" className="primary-button" onClick={() => setDialog({ kind: 'create' })}>
              + New user
            </button>
          )
        }
      />

      <div className="filter-bar">
        <div className="segmented">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.id}
              type="button"
              className={`segmented-item ${status === f.id ? 'segmented-item--active' : ''}`}
              onClick={() => setStatus(f.id)}
            >
              {f.label}
              <span className="segmented-count">{counts[f.id]}</span>
            </button>
          ))}
        </div>
        <select className="select-input" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)} aria-label="Filter by role">
          <option value="">All roles</option>
          {roles.map((r) => (
            <option key={r.name} value={r.name}>
              {r.name}
            </option>
          ))}
        </select>
      </div>

      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

      <UsersTable
        users={filtered}
        loading={loading}
        currentUserId={currentUserId}
        canWrite={can(P.USERS_WRITE)}
        canDelete={can(P.USERS_DELETE)}
        onEdit={(user) => setDialog({ kind: 'edit', user })}
        onResetPassword={(user) => setDialog({ kind: 'password', user })}
        onToggleEnabled={toggleEnabled}
        onDelete={confirmDelete}
      />

      <Suspense fallback={<SectionSpinner overlay />}>
        {(dialog?.kind === 'create' || dialog?.kind === 'edit') && (
          <UserFormModal
            mode={dialog.kind}
            user={dialog.user}
            roles={roles}
            isSelf={dialog.user?.id === currentUserId}
            onClose={close}
            onCreate={async (payload) => {
              await create(payload);
              notify(`${payload.username} created`);
            }}
            onUpdate={async (id, payload) => {
              await update(id, payload);
              notify('User saved');
            }}
          />
        )}
        {dialog?.kind === 'password' && (
          <ResetPasswordModal
            user={dialog.user}
            onClose={close}
            onReset={async (user, payload) => {
              await resetPassword(user.id, payload);
              notify(`Password reset for ${user.username}`);
            }}
          />
        )}
        {dialog?.kind === 'confirm' && <ConfirmDialog {...dialog} onClose={close} />}
      </Suspense>
    </>
  );
}
