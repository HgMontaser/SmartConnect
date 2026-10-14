import { lazy, Suspense, useMemo, useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import RolePill from '../components/RolePill.jsx';
import { EditIcon, LockIcon, TrashIcon } from '../components/icons.jsx';
import { P, groupPermissions, splitPermission } from '../permissions.js';

const PermissionFormModal = lazy(() => import('../components/PermissionFormModal.jsx'));
const ConfirmDialog = lazy(() => import('../components/ConfirmDialog.jsx'));

export default function PermissionsPage({ permissionsState, can, api, notify }) {
  const { items: permissions, loading, error, setError } = permissionsState;
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState(null);
  const canWrite = can(P.PERMISSIONS_WRITE);

  const groups = useMemo(() => {
    const q = search.trim().toLowerCase();
    const matching = q
      ? permissions.filter((p) => [p.name, p.description].some((f) => (f || '').toLowerCase().includes(q)))
      : permissions;
    return groupPermissions(matching);
  }, [permissions, search]);

  const close = () => setDialog(null);

  return (
    <>
      <PageHeader
        title="Permissions"
        subtitle="The individual actions the API checks. Grant them to users by adding them to a role."
        search={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search permissions"
        action={
          canWrite && (
            <button type="button" className="primary-button" onClick={() => setDialog({ kind: 'create' })}>
              + New permission
            </button>
          )
        }
      />

      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

      {loading && (
        <div className="table-card table-empty">
          <SectionSpinner label="Loading permissions…" />
        </div>
      )}
      {!loading && groups.length === 0 && <div className="table-card table-empty">No permissions found.</div>}

      {!loading &&
        groups.map(([resource, items]) => (
          <section key={resource} className="table-card">
            <div className="group-header">
              <span className="group-title">{resource}</span>
              <span className="group-count">{items.length}</span>
            </div>
            <table className="data-table data-table--fixed">
              <thead>
                <tr>
                  <th>Permission</th>
                  <th>Granted by</th>
                  {canWrite && <th className="col-actions" aria-label="Actions" />}
                </tr>
              </thead>
              <tbody>
                {items.map((perm) => (
                  <tr key={perm.name}>
                    <td>
                      <div className="name-cell">
                        <span className="name-primary">
                          <span className="mono-strong">
                            <span className="perm-resource">{resource}:</span>
                            {splitPermission(perm.name).action}
                          </span>
                          {perm.system && (
                            <span className="system-badge" title="Checked by the API itself, can't be deleted">
                              <LockIcon />
                              system
                            </span>
                          )}
                        </span>
                        <span className="name-secondary">{perm.description || 'No description'}</span>
                      </div>
                    </td>
                    <td>
                      <div className="role-pill-group">
                        {perm.roles.map((r) => (
                          <RolePill key={r} role={r} />
                        ))}
                      </div>
                    </td>
                    {canWrite && (
                      <td>
                        <div className="row-actions">
                          <button
                            type="button"
                            className="icon-button"
                            onClick={() => setDialog({ kind: 'edit', permission: perm })}
                            title="Edit"
                            aria-label="Edit permission"
                          >
                            <EditIcon />
                          </button>
                          <button
                            type="button"
                            className="icon-button icon-button--danger"
                            disabled={perm.system}
                            title={perm.system ? "System permissions can't be deleted" : 'Delete'}
                            aria-label="Delete permission"
                            onClick={() =>
                              setDialog({
                                kind: 'confirm',
                                title: 'Delete permission',
                                message: `Delete ${perm.name}? It will be removed from ${perm.roles.length} role${perm.roles.length === 1 ? '' : 's'}.`,
                                confirmLabel: 'Delete permission',
                                danger: true,
                                onConfirm: async () => {
                                  await api.remove(perm.name);
                                  notify(`Permission ${perm.name} deleted`);
                                },
                              })
                            }
                          >
                            <TrashIcon />
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        ))}

      <Suspense fallback={<SectionSpinner overlay />}>
        {(dialog?.kind === 'create' || dialog?.kind === 'edit') && (
          <PermissionFormModal
            mode={dialog.kind}
            permission={dialog.permission}
            onClose={close}
            onCreate={async (payload) => {
              await api.create(payload);
              notify(`Permission ${payload.name} created`);
            }}
            onUpdate={async (name, payload) => {
              await api.update(name, payload);
              notify(`Permission ${name} saved`);
            }}
          />
        )}
        {dialog?.kind === 'confirm' && <ConfirmDialog {...dialog} onClose={close} />}
      </Suspense>
    </>
  );
}
