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
        subtitle="The actions the APIs check. Services publish their own; grant them to users through roles."
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
        groups.map(([resource, items]) => {
          const owners = new Set(items.map((p) => p.owner || null));
          const groupOwner = owners.size === 1 ? items[0].owner : null;
          return (
            <section key={resource} className="table-card">
              <div className="group-header">
                <span className="group-title">{resource}</span>
                <span className="group-count">{items.length}</span>
                {groupOwner && (
                  <span className="owner-badge" title="Defined by this service and synced automatically">
                    managed by {groupOwner}
                  </span>
                )}
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
                  {items.map((perm) => {
                    const managed = Boolean(perm.owner);
                    const deletable = !perm.system && (!managed || perm.orphaned);
                    return (
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
                              {perm.owner && !groupOwner && <span className="owner-badge">{perm.owner}</span>}
                              {perm.orphaned && (
                                <span
                                  className="orphan-badge"
                                  title={`${perm.owner} no longer declares this permission. Delete it once you're sure nothing needs it.`}
                                >
                                  orphaned
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
                                disabled={managed}
                                title={managed ? `Managed by ${perm.owner} - change it in that service` : 'Edit'}
                                aria-label="Edit permission"
                              >
                                <EditIcon />
                              </button>
                              <button
                                type="button"
                                className="icon-button icon-button--danger"
                                disabled={!deletable}
                                title={
                                  perm.system
                                    ? "System permissions can't be deleted"
                                    : !deletable
                                      ? `Managed by ${perm.owner} - remove it from that service first`
                                      : 'Delete'
                                }
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
                    );
                  })}
                </tbody>
              </table>
            </section>
          );
        })}

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
