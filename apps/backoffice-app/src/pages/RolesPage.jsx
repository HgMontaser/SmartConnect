import { Suspense, useMemo, useState } from 'react';
import lazyWithReload from '../utils/lazyWithReload.js';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import { EditIcon, LockIcon, TrashIcon } from '../components/icons.jsx';
import { P } from '../permissions.js';

const RoleFormModal = lazyWithReload(() => import('../components/RoleFormModal.jsx'));
const ConfirmDialog = lazyWithReload(() => import('../components/ConfirmDialog.jsx'));

export default function RolesPage({ rolesState, permissions, can, api, notify }) {
  const { items: roles, loading, error, setError } = rolesState;
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState(null);
  const canWrite = can(P.ROLES_WRITE);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return roles;
    return roles.filter((r) => [r.name, r.description].some((f) => (f || '').toLowerCase().includes(q)));
  }, [roles, search]);

  const close = () => setDialog(null);

  return (
    <>
      <PageHeader
        title="Roles"
        subtitle="A role bundles permissions. Users get permissions only through the roles they hold."
        search={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search roles"
        action={
          canWrite && (
            <button type="button" className="primary-button" onClick={() => setDialog({ kind: 'create' })}>
              + New role
            </button>
          )
        }
      />

      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

      <div className="table-card">
        <table className="data-table">
          <thead>
            <tr>
              <th>Role</th>
              <th>Permissions</th>
              <th>Users</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={4} className="table-empty">
                  <SectionSpinner label="Loading roles…" />
                </td>
              </tr>
            )}
            {!loading && filtered.length === 0 && (
              <tr>
                <td colSpan={4} className="table-empty">
                  No roles found.
                </td>
              </tr>
            )}
            {!loading &&
              filtered.map((role) => (
                <tr key={role.name} className="clickable-row" onClick={() => setDialog({ kind: 'edit', role })}>
                  <td>
                    <div className="name-cell">
                      <span className="name-primary">
                        <span className="mono-strong">{role.name}</span>
                        {role.system && (
                          <span className="system-badge" title="Built-in role, can't be deleted">
                            <LockIcon />
                            system
                          </span>
                        )}
                      </span>
                      <span className="name-secondary">{role.description || 'No description'}</span>
                    </div>
                  </td>
                  <td>
                    {role.permissions.length === 0 ? (
                      <span className="muted-text">None</span>
                    ) : (
                      <div className="perm-chip-group">
                        {role.permissions.slice(0, 4).map((p) => (
                          <span key={p} className="perm-chip">
                            {p}
                          </span>
                        ))}
                        {role.permissions.length > 4 && (
                          <span className="perm-chip perm-chip--more">+{role.permissions.length - 4}</span>
                        )}
                      </div>
                    )}
                  </td>
                  <td>
                    <span className="count-cell">{role.userCount}</span>
                  </td>
                  <td>
                    <div className="row-actions" onClick={(e) => e.stopPropagation()}>
                      {canWrite && (
                        <button
                          type="button"
                          className="icon-button"
                          onClick={() => setDialog({ kind: 'edit', role })}
                          title="Edit"
                          aria-label="Edit role"
                        >
                          <EditIcon />
                        </button>
                      )}
                      {canWrite && (
                        <button
                          type="button"
                          className="icon-button icon-button--danger"
                          disabled={role.system}
                          title={role.system ? "System roles can't be deleted" : 'Delete'}
                          aria-label="Delete role"
                          onClick={() =>
                            setDialog({
                              kind: 'confirm',
                              title: 'Delete role',
                              message:
                                role.userCount > 0
                                  ? `Delete ${role.name}? ${role.userCount} user${role.userCount === 1 ? '' : 's'} will lose it and every permission it grants.`
                                  : `Delete ${role.name}? This cannot be undone.`,
                              confirmLabel: 'Delete role',
                              danger: true,
                              onConfirm: async () => {
                                await api.remove(role.name);
                                notify(`Role ${role.name} deleted`);
                              },
                            })
                          }
                        >
                          <TrashIcon />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      <Suspense fallback={<SectionSpinner overlay />}>
        {(dialog?.kind === 'create' || dialog?.kind === 'edit') && (
          <RoleFormModal
            mode={dialog.kind}
            role={dialog.role}
            permissions={permissions}
            canWrite={canWrite}
            loadMembers={can(P.USERS_READ) ? api.members : undefined}
            onClose={close}
            onCreate={async (payload) => {
              await api.create(payload);
              notify(`Role ${payload.name} created`);
            }}
            onUpdate={async (name, payload) => {
              await api.update(name, payload);
              notify(`Role ${name} saved`);
            }}
          />
        )}
        {dialog?.kind === 'confirm' && <ConfirmDialog {...dialog} onClose={close} />}
      </Suspense>
    </>
  );
}

