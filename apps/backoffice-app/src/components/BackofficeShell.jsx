import { useCallback, useMemo, useState } from 'react';
import Topbar from './Topbar.jsx';
import Sidebar from './Sidebar.jsx';
import UsersTable from './UsersTable.jsx';
import EventStreamRail from './EventStreamRail.jsx';
import UserFormModal from './UserFormModal.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import useUsers from '../hooks/useUsers.js';
import useUserEventsSocket from '../hooks/useUserEventsSocket.js';

const MAX_EVENTS = 50;
let eventSeq = 0;

function displayEventName(eventType) {
  switch (eventType) {
    case 'USER_CREATED':
      return 'user.created';
    case 'USER_UPDATED':
      return 'user.updated';
    case 'USER_DELETED':
      return 'user.deleted';
    default:
      return (eventType || 'unknown').toLowerCase().replace(/_/g, '.');
  }
}

function describeEvent(event) {
  const username = event.username || 'a user';
  const role =
    Array.isArray(event.realmRoles) && event.realmRoles.length > 0
      ? event.realmRoles.join(', ')
      : 'user';
  switch (event.eventType) {
    case 'USER_CREATED':
      return `${username} was created with role ${role}`;
    case 'USER_UPDATED':
      return `${username} was updated`;
    case 'USER_DELETED':
      return `${username} was deleted`;
    default:
      return `${username} triggered ${event.eventType || 'an unknown event'}`;
  }
}

export default function BackofficeShell({ keycloak }) {
  const authenticated = true;
  const { users, loading, error, setError, applyUserEvent, create, update, remove } = useUsers({
    keycloak,
    authenticated,
  });
  const [events, setEvents] = useState([]);
  const [search, setSearch] = useState('');
  const [modalState, setModalState] = useState(null); // null | { mode: 'create' } | { mode: 'edit', user }
  const [actionError, setActionError] = useState(null);

  // Single WebSocket instance feeding both the event-stream rail and the
  // users-table "Last sync" indicators — one useEffect, one connection.
  const handleSocketEvent = useCallback(
    (event) => {
      eventSeq += 1;
      setEvents((prev) =>
        [
          {
            id: `${event.userId || 'evt'}-${event.occurredAt || Date.now()}-${eventSeq}`,
            eventType: event.eventType,
            displayName: displayEventName(event.eventType),
            description: describeEvent(event),
            occurredAt: event.occurredAt || new Date().toISOString(),
          },
          ...prev,
        ].slice(0, MAX_EVENTS)
      );
      applyUserEvent(event);
    },
    [applyUserEvent]
  );

  const { connected } = useUserEventsSocket({ keycloak, authenticated, onEvent: handleSocketEvent });

  const filteredUsers = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return users;
    return users.filter((u) => [u.username, u.email].some((field) => (field || '').toLowerCase().includes(q)));
  }, [users, search]);

  const initials = useMemo(() => {
    const name = keycloak?.tokenParsed?.preferred_username || keycloak?.tokenParsed?.name || '';
    return name ? name.slice(0, 2).toUpperCase() : '?';
  }, [keycloak]);

  const handleDelete = async (user) => {
    const confirmed = window.confirm(`Delete ${user.username}? This cannot be undone.`);
    if (!confirmed) return;
    try {
      await remove(user.id);
    } catch (err) {
      setActionError(err.message || 'Failed to delete user.');
    }
  };

  return (
    <div className="shell">
      <Topbar connected={connected} search={search} onSearchChange={setSearch} initials={initials} />
      <div className="shell-body">
        <Sidebar />
        <main className="shell-main">
          {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}
          {actionError && <ErrorBanner message={actionError} onDismiss={() => setActionError(null)} />}

          <div className="main-header">
            <h1 className="page-heading">Users</h1>
            <button type="button" className="primary-button" onClick={() => setModalState({ mode: 'create' })}>
              + New user
            </button>
          </div>

          <UsersTable
            users={filteredUsers}
            loading={loading}
            onEdit={(user) => setModalState({ mode: 'edit', user })}
            onDelete={handleDelete}
          />
        </main>
        <EventStreamRail events={events} />
      </div>

      {modalState && (
        <UserFormModal
          mode={modalState.mode}
          user={modalState.user}
          onClose={() => setModalState(null)}
          onCreate={create}
          onUpdate={update}
        />
      )}
    </div>
  );
}
