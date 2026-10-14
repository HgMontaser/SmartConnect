export default function RolePill({ role }) {
  return <span className={`role-pill ${role === 'admin' ? 'role-pill--admin' : ''}`}>{role}</span>;
}
