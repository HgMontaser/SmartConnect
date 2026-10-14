const COMING_SOON = ['Sessions', 'Event log', 'Gateway health', 'Settings'];

export default function Sidebar({ items, active, onNavigate }) {
  return (
    <nav className="sidebar">
      <span className="nav-section">Access</span>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`nav-item ${active === item.id ? 'nav-item--active' : ''}`}
          onClick={() => onNavigate(item.id)}
        >
          <item.icon />
          <span>{item.label}</span>
          {item.count !== undefined && <span className="nav-count">{item.count}</span>}
        </button>
      ))}

      <span className="nav-section">Coming soon</span>
      {COMING_SOON.map((label) => (
        <button key={label} type="button" className="nav-item nav-item--soon" disabled>
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}
