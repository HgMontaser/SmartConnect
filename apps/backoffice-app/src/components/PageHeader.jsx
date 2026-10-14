import { SearchIcon } from './icons.jsx';

export default function PageHeader({ title, subtitle, search, onSearchChange, searchPlaceholder, action }) {
  return (
    <div className="page-header">
      <div className="page-header-top">
        <div>
          <h1 className="page-heading">{title}</h1>
          {subtitle && <p className="page-subtitle">{subtitle}</p>}
        </div>
        {action}
      </div>
      {onSearchChange && (
        <div className="search-box">
          <SearchIcon />
          <input
            className="search-input"
            type="text"
            placeholder={searchPlaceholder}
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
      )}
    </div>
  );
}
