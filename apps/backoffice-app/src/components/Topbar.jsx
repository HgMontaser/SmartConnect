import { SearchIcon } from './icons.jsx';

export default function Topbar({ connected, search, onSearchChange, initials }) {
  return (
    <header className="topbar">
      <div className="topbar-left">
        <span className="wordmark">SmartConnect</span>
        <span className="topbar-label">Backoffice</span>
        <span className="realm-chip">smartconnect</span>
      </div>

      <div className="topbar-search">
        <SearchIcon />
        <input
          className="search-input"
          type="text"
          placeholder="Search users by username or email"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>

      <div className="topbar-right">
        <span className={`kafka-pill ${connected ? 'kafka-pill--live' : 'kafka-pill--down'}`}>
          <span className={`live-dot ${connected ? 'live-dot--success' : 'live-dot--warn'}`} />
          {connected ? 'Kafka · live · 1 consumers' : 'Kafka · reconnecting...'}
        </span>
        <span className="avatar" title={initials}>
          {initials}
        </span>
      </div>
    </header>
  );
}
