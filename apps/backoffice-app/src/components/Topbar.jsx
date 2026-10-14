import { useEffect, useRef, useState } from 'react';
import { LogoutIcon } from './icons.jsx';

export default function Topbar({ connected, initials, username, email, onSignOut }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    if (!menuOpen) return undefined;
    const onDown = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [menuOpen]);

  return (
    <header className="topbar">
      <div className="topbar-left">
        <span className="wordmark">SmartConnect</span>
        <span className="topbar-label">Backoffice</span>
        <span className="realm-chip">smartconnect</span>
      </div>

      <div className="topbar-right">
        <span className={`kafka-pill ${connected ? 'kafka-pill--live' : 'kafka-pill--down'}`}>
          <span className={`live-dot ${connected ? 'live-dot--success' : 'live-dot--warn'}`} />
          {connected ? 'Kafka · live' : 'Kafka · reconnecting…'}
        </span>
        <div className="user-menu" ref={menuRef}>
          <button
            type="button"
            className="avatar"
            title={username}
            onClick={() => setMenuOpen((open) => !open)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
          >
            {initials}
          </button>
          {menuOpen && (
            <div className="user-menu-popover" role="menu">
              <div className="user-menu-identity">
                <span className="user-menu-name">{username}</span>
                {email && <span className="user-menu-email">{email}</span>}
              </div>
              <button type="button" className="user-menu-item" role="menuitem" onClick={onSignOut}>
                <LogoutIcon />
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
