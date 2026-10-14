import { groupPermissions, splitPermission } from '../permissions.js';

export default function PermissionPicker({ permissions, selected, onChange, disabled = false }) {
  const groups = groupPermissions(permissions);

  const toggle = (name) => {
    const next = new Set(selected);
    if (next.has(name)) next.delete(name);
    else next.add(name);
    onChange([...next]);
  };

  const toggleGroup = (items) => {
    const next = new Set(selected);
    const allOn = items.every((p) => next.has(p.name));
    items.forEach((p) => (allOn ? next.delete(p.name) : next.add(p.name)));
    onChange([...next]);
  };

  if (permissions.length === 0) {
    return <p className="muted-text">No permissions exist yet.</p>;
  }

  return (
    <div className="perm-picker">
      {groups.map(([resource, items]) => {
        const onCount = items.filter((p) => selected.includes(p.name)).length;
        return (
          <div key={resource} className="perm-group">
            <div className="perm-group-header">
              <span className="perm-group-name">{resource}</span>
              <span className="perm-group-count">
                {onCount}/{items.length}
              </span>
              {!disabled && (
                <button type="button" className="link-button" onClick={() => toggleGroup(items)}>
                  {onCount === items.length ? 'Clear' : 'All'}
                </button>
              )}
            </div>
            {items.map((p) => (
              <label key={p.name} className={`perm-option ${disabled ? 'perm-option--disabled' : ''}`}>
                <input
                  type="checkbox"
                  checked={selected.includes(p.name)}
                  onChange={() => toggle(p.name)}
                  disabled={disabled}
                />
                <span className="perm-option-text">
                  <span className="perm-option-action">{splitPermission(p.name).action || p.name}</span>
                  {p.description && <span className="perm-option-desc">{p.description}</span>}
                </span>
              </label>
            ))}
          </div>
        );
      })}
    </div>
  );
}
