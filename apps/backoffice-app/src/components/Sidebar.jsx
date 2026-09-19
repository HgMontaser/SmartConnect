const NAV_ITEMS = [
  { label: 'Users', active: true },
  { label: 'Roles & permissions' },
  { label: 'Sessions' },
  { label: 'Event log' },
  { label: 'Gateway health' },
  { label: 'Settings' },
];

export default function Sidebar() {
  return (
    <nav className="sidebar">
      {NAV_ITEMS.map((item) => (
        <button
          key={item.label}
          type="button"
          className={`nav-item ${item.active ? 'nav-item--active' : ''}`}
          disabled={!item.active}
          title={item.active ? undefined : 'Coming soon'}
        >
          {item.label}
        </button>
      ))}
    </nav>
  );
}
