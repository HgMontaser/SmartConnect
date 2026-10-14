import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Topbar from './Topbar.jsx';
import Sidebar from './Sidebar.jsx';
import Toasts from './Toasts.jsx';
import { PageSpinner, SectionSpinner } from './Spinner.jsx';
import PageErrorBoundary from './PageErrorBoundary.jsx';
import lazyWithReload from '../utils/lazyWithReload.js';
import { AuditIcon, ChartIcon, DevicesIcon, KeyIcon, PulseIcon, ShieldIcon, StreamIcon, UsersIcon } from './icons.jsx';
import useUsers from '../hooks/useUsers.js';
import useCollection from '../hooks/useCollection.js';
import useHashRoute from '../hooks/useHashRoute.js';
import useUserEventsSocket from '../hooks/useUserEventsSocket.js';
import * as api from '../api.js';
import { P, permissionsFromToken } from '../permissions.js';

// Pages load on first visit; the shell (topbar, sidebar) stays up while they do.
const UsersPage = lazyWithReload(() => import('../pages/UsersPage.jsx'));
const RolesPage = lazyWithReload(() => import('../pages/RolesPage.jsx'));
const PermissionsPage = lazyWithReload(() => import('../pages/PermissionsPage.jsx'));
const GatewayHealthPage = lazyWithReload(() => import('../pages/GatewayHealthPage.jsx'));
const TrafficPage = lazyWithReload(() => import('../pages/TrafficPage.jsx'));
const SessionsPage = lazyWithReload(() => import('../pages/SessionsPage.jsx'));
const EventsPage = lazyWithReload(() => import('../pages/EventsPage.jsx'));
const AuditPage = lazyWithReload(() => import('../pages/AuditPage.jsx'));
const NoAccess = lazyWithReload(() => import('./NoAccess.jsx'));

const MAX_EVENTS = 50;
const TOAST_MS = 3200;
let toastSeq = 0;

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

  // Every Kafka event, pushed as it lands (one socket): user events also update the users
  // table; the Events page's live header shows them all.
  const [liveEvents, setLiveEvents] = useState([]);
  const { applyUserEvent } = usersState;
  const handleSocketEvent = useCallback(
    (event) => {
      setLiveEvents((prev) => [{ ...event, receivedAt: Date.now() }, ...prev].slice(0, MAX_EVENTS));
      if (event.user) applyUserEvent(event.user);
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
        { id: 'traffic', label: 'Traffic', icon: ChartIcon, permission: P.PLATFORM_READ },
        { id: 'sessions', label: 'Sessions', icon: DevicesIcon, permission: P.SESSIONS_READ },
        { id: 'events', label: 'Events', icon: StreamIcon, permission: P.EVENTS_READ },
        { id: 'audit', label: 'Audit', icon: AuditIcon, permission: P.AUDIT_READ },
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
          <PageErrorBoundary key={active}>
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
              {active === 'traffic' && <TrafficPage getToken={getToken} />}
              {active === 'sessions' && (
                <SessionsPage getToken={getToken} can={can} currentSessionId={tokenParsed?.sid} notify={notify} />
              )}
              {active === 'events' && <EventsPage getToken={getToken} canTrace={can(P.LOGS_READ)} liveEvents={liveEvents} connected={connected} />}
              {active === 'audit' && <AuditPage getToken={getToken} canTrace={can(P.LOGS_READ)} />}
            </Suspense>
          </PageErrorBoundary>
        </main>
      </div>
      <Toasts toasts={toasts} />
    </div>
  );
}
