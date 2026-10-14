export default function Sidebar({ sections, active, onNavigate }) {
  return (
    <nav className="sidebar">
      {sections.map((section) => (
        <div key={section.label} className="nav-group">
          <span className="nav-section">{section.label}</span>
          {section.items.map((item) => (
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
        </div>
      ))}

      <span className="nav-section">Coming soon</span>
      <button type="button" className="nav-item nav-item--soon" disabled>
        <span>Settings</span>
      </button>
    </nav>
  );
}
