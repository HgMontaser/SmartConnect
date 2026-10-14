export function EditIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M13.5 3.5L16.5 6.5L7 16H4V13L13.5 3.5Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function TrashIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M4 6h12M8 6V4.5A1.5 1.5 0 0 1 9.5 3h1A1.5 1.5 0 0 1 12 4.5V6M6 6v9.5A1.5 1.5 0 0 0 7.5 17h5a1.5 1.5 0 0 0 1.5-1.5V6"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function CloseIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M5 5L15 15M15 5L5 15" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  );
}

export function SearchIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.7" />
      <path d="M17 17L13.4 13.4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  );
}

function Icon({ children }) {
  return (
    <svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      {children}
    </svg>
  );
}

export function UsersIcon() {
  return (
    <Icon>
      <circle cx="8" cy="7" r="3" />
      <path d="M2.5 16.5c.6-2.8 2.8-4.5 5.5-4.5s4.9 1.7 5.5 4.5M13 4.2a3 3 0 0 1 0 5.6M15 12.3c1.3.6 2.2 2 2.5 4.2" />
    </Icon>
  );
}

export function ShieldIcon() {
  return (
    <Icon>
      <path d="M10 2.5l6 2.3v4.6c0 3.8-2.5 6.7-6 8.1-3.5-1.4-6-4.3-6-8.1V4.8l6-2.3z" />
      <path d="M7.5 10l1.8 1.8 3.4-3.6" />
    </Icon>
  );
}

export function KeyIcon() {
  return (
    <Icon>
      <circle cx="7" cy="13" r="3.5" />
      <path d="M9.5 10.5L16 4M13.5 6.5l2 2M15 5l1.5 1.5" />
    </Icon>
  );
}

export function PauseIcon() {
  return (
    <Icon>
      <circle cx="10" cy="10" r="7" />
      <path d="M8.3 7.5v5M11.7 7.5v5" />
    </Icon>
  );
}

export function PlayIcon() {
  return (
    <Icon>
      <circle cx="10" cy="10" r="7" />
      <path d="M8.5 7.3l4 2.7-4 2.7z" />
    </Icon>
  );
}

export function LockIcon() {
  return (
    <Icon>
      <rect x="4.5" y="9" width="11" height="8" rx="1.5" />
      <path d="M7 9V6.5a3 3 0 0 1 6 0V9" />
    </Icon>
  );
}

export function LogoutIcon() {
  return (
    <Icon>
      <path d="M8 3.5H5A1.5 1.5 0 0 0 3.5 5v10A1.5 1.5 0 0 0 5 16.5h3M12.5 13.5L16 10l-3.5-3.5M16 10H8" />
    </Icon>
  );
}

export function ClockIcon() {
  return (
    <Icon>
      <circle cx="10" cy="10" r="7" />
      <path d="M10 6.5V10l2.5 1.5" />
    </Icon>
  );
}

export function PulseIcon() {
  return (
    <Icon>
      <path d="M2.5 10h3l2-5 3.5 10 2-5h4.5" />
    </Icon>
  );
}

export function DevicesIcon() {
  return (
    <Icon>
      <rect x="2.5" y="4" width="11" height="8" rx="1.2" />
      <path d="M5.5 15h5M8 12v3M15 7.5h2a.5.5 0 0 1 .5.5v7a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1-.5-.5V8a.5.5 0 0 1 .5-.5z" />
    </Icon>
  );
}

export function StreamIcon() {
  return (
    <Icon>
      <path d="M3 5.5h14M3 10h14M3 14.5h9" />
    </Icon>
  );
}

export function RefreshIcon() {
  return (
    <Icon>
      <path d="M16 10a6 6 0 1 1-1.8-4.3M16 3.5v3h-3" />
    </Icon>
  );
}

export function AuditIcon() {
  return (
    <Icon>
      <path d="M6 3.5h6.5L16 7v9.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-12a1 1 0 0 1 1-1z" />
      <path d="M12.5 3.5V7H16M7.5 11l1.8 1.8 3.4-3.6" />
    </Icon>
  );
}

export function AlertIcon() {
  return (
    <Icon>
      <path d="M10 3.5 17 16H3z" />
      <path d="M10 8.5v3.2M10 13.8v.2" />
    </Icon>
  );
}
