import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Topbar from './Topbar.jsx';
import Sidebar from './Sidebar.jsx';
import Toasts from './Toasts.jsx';
import { PageSpinner, SectionSpinner } from './Spinner.jsx';
import { DevicesIcon, KeyIcon, PulseIcon, ShieldIcon, StreamIcon, UsersIcon } from './icons.jsx';
import useUsers from '../hooks/useUsers.js';
import useCollection from '../hooks/useCollection.js';
import useHashRoute from '../hooks/useHashRoute.js';
import useUserEventsSocket from '../hooks/useUserEventsSocket.js';
import * as api from '../api.js';
import { P, permissionsFromToken } from '../permissions.js';

// Pages load on first visit; the shell (topbar, sidebar) stays up while they do.
const UsersPage = lazy(() => import('../pages/UsersPage.jsx'));
const RolesPage = lazy(() => import('../pages/RolesPage.jsx'));
const PermissionsPage = lazy(() => import('../pages/PermissionsPage.jsx'));
const GatewayHealthPage = lazy(() => import('../pages/GatewayHealthPage.jsx'));
const SessionsPage = lazy(() => import('../pages/SessionsPage.jsx'));
const EventLogPage = lazy(() => import('../pages/EventLogPage.jsx'));
const EventStreamRail = lazy(() => import('./EventStreamRail.jsx'));
const NoAccess = lazy(() => import('./NoAccess.jsx'));

const MAX_EVENTS = 50;
const TOAST_MS = 3200;
let eventSeq = 0;
let toastSeq = 0;

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
  const roles = Array.isArray(event.realmRoles) && event.realmRoles.length > 0 ? event.realmRoles.join(', ') : 'no roles';
  switch (event.eventType) {
    case 'USER_CREATED':
      return `${username} was created with ${roles}`;
    case 'USER_UPDATED':
      return `${username} was updated`;
    case 'USER_DELETED':
      return `${username} was deleted`;
    default:
      return `${username} triggered ${event.eventType || 'an unknown event'}`;
  }
}

export default function BackofficeShell({ keycloak }) {
  // Permissions come from the access token. Re-read it whenever keycloak-js refreshes it, and
  // force a refresh after any role/permission change so our own access updates immediately.
  const [tokenParsed, setTokenParsed] = useState(keycloak.tokenParsed);
  useEffect(() => {
    keycloak.onAuthRefreshSuccess = () => setTokenParsed(keycloak.tokenParsed);
    return () => {
      keycloak.onAuthRefreshSuccess = undefined;
    };
  }, [keycloak]);

  const granted = useMemo(() => permissionsFromToken(tokenParsed), [tokenParsed]);
  const can = useCallback((permission) => granted.has(permission), [granted]);
  const getToken = useCallback(() => keycloak.token, [keycloak]);

  const [toasts, setToasts] = useState([]);
  const notify = useCallback((message) => {
    toastSeq += 1;
    const id = toastSeq;
    setToasts((prev) => [...prev, { id, message }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), TOAST_MS);
  }, []);

  const usersState = useUsers({ keycloak, authenticated: can(P.USERS_READ) });
  const loadRoles = useCallback(() => api.listRoles(getToken()), [getToken]);
  const loadPermissions = useCallback(() => api.listPermissions(getToken()), [getToken]);
  const rolesState = useCollection(loadRoles, { enabled: can(P.ROLES_READ) });
  const permissionsState = useCollection(loadPermissions, { enabled: can(P.PERMISSIONS_READ) });

  // Without roles:read, offer the roles we can already see on users rather than nothing.
  const roleOptions = useMemo(() => {
    if (can(P.ROLES_READ)) return rolesState.items;
    const names = new Set(usersState.users.flatMap((u) => u.realmRoles || []));
    return [...names].sort().map((name) => ({ name }));
  }, [can, rolesState.items, usersState.users]);

  const reloadRef = useRef();
  reloadRef.current = async () => {
    try {
      await keycloak.updateToken(-1);
      setTokenParsed(keycloak.tokenParsed);
    } catch {
      // Refresh failure is handled by App's onTokenExpired; the lists below still reload.
    }
    rolesState.reload();
    permissionsState.reload();
    usersState.refetch();
  };

  const roleApi = useMemo(
    () => ({
      create: async (payload) => {
        await api.createRole(payload, getToken());
        await reloadRef.current();
      },
      update: async (name, payload) => {
        await api.updateRole(name, payload, getToken());
        await reloadRef.current();
      },
      remove: async (name) => {
        await api.deleteRole(name, getToken());
        await reloadRef.current();
      },
      members: (name) => api.listRoleMembers(name, getToken()),
    }),
    [getToken]
  );

  const permissionApi = useMemo(
    () => ({
      create: async (payload) => {
        await api.createPermission(payload, getToken());
        await reloadRef.current();
      },
      update: async (name, payload) => {
        await api.updatePermission(name, payload, getToken());
        await reloadRef.current();
      },
      remove: async (name) => {
        await api.deletePermission(name, getToken());
        await reloadRef.current();
      },
    }),
    [getToken]
  );

  const [events, setEvents] = useState([]);
  const { applyUserEvent } = usersState;
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
  const { connected } = useUserEventsSocket({ keycloak, authenticated: true, onEvent: handleSocketEvent });

  // Without permissions:read, the role editor can still show the permissions roles already hold.
  const permissionOptions = useMemo(() => {
    if (can(P.PERMISSIONS_READ)) return permissionsState.items;
    const names = new Set(rolesState.items.flatMap((r) => r.permissions || []));
    return [...names].sort().map((name) => ({ name }));
  }, [can, permissionsState.items, rolesState.items]);

  const navSections = [
    {
      label: 'Access',
      items: [
        { id: 'users', label: 'Users', icon: UsersIcon, permission: P.USERS_READ, count: usersState.users.length },
        { id: 'roles', label: 'Roles', icon: ShieldIcon, permission: P.ROLES_READ, count: rolesState.items.length },
        { id: 'permissions', label: 'Permissions', icon: KeyIcon, permission: P.PERMISSIONS_READ, count: permissionsState.items.length },
      ],
    },
    {
      label: 'Platform',
      items: [
        { id: 'gateway', label: 'Gateway health', icon: PulseIcon, permission: P.PLATFORM_READ },
        { id: 'sessions', label: 'Sessions', icon: DevicesIcon, permission: P.SESSIONS_READ },
        { id: 'events', label: 'Event log', icon: StreamIcon, permission: P.EVENTS_READ },
      ],
    },
  ]
    .map((section) => ({ ...section, items: section.items.filter((item) => can(item.permission)) }))
    .filter((section) => section.items.length > 0);
  const navItems = navSections.flatMap((section) => section.items);

  const [route, navigate] = useHashRoute();
  const active = navItems.some((i) => i.id === route) ? route : navItems[0]?.id;

  const username = tokenParsed?.preferred_username || tokenParsed?.name || '';
  const initials = username ? username.slice(0, 2).toUpperCase() : '?';
  const signOut = () => keycloak.logout({ redirectUri: window.location.origin });

  if (navItems.length === 0) {
    return (
      <Suspense fallback={<PageSpinner />}>
        <NoAccess onSignOut={signOut} />
      </Suspense>
    );
  }

  return (
    <div className="shell">
      <Topbar connected={connected} initials={initials} username={username} email={tokenParsed?.email} onSignOut={signOut} />
      <div className="shell-body">
        <Sidebar sections={navSections} active={active} onNavigate={navigate} />
        <main className="shell-main">
          <Suspense fallback={<SectionSpinner label="Loading page…" />}>
            {active === 'users' && (
              <UsersPage
                usersState={usersState}
                roles={roleOptions}
                can={can}
                currentUserId={tokenParsed?.sub}
                notify={notify}
              />
            )}
            {active === 'roles' && (
              <RolesPage
                rolesState={rolesState}
                permissions={permissionOptions}
                can={can}
                api={roleApi}
                notify={notify}
              />
            )}
            {active === 'permissions' && (
              <PermissionsPage permissionsState={permissionsState} can={can} api={permissionApi} notify={notify} />
            )}
            {active === 'gateway' && <GatewayHealthPage getToken={getToken} />}
            {active === 'sessions' && (
              <SessionsPage getToken={getToken} can={can} currentSessionId={tokenParsed?.sid} notify={notify} />
            )}
            {active === 'events' && <EventLogPage getToken={getToken} />}
          </Suspense>
        </main>
        {active === 'users' && (
          <Suspense fallback={null}>
            <EventStreamRail events={events} />
          </Suspense>
        )}
      </div>
      <Toasts toasts={toasts} />
    </div>
  );
}
