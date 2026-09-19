import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '../api.js';

// How long a row shows the pulsing "just updated live" indicator after a WS event.
const JUST_UPDATED_MS = 5000;

/**
 * Owns the users list: the one-time initial GET /api/users on mount, plus CRUD
 * actions, plus applyUserEvent() which merges live WebSocket events into local
 * state. There is no polling — after mount, the only thing that mutates this
 * list besides direct user actions is applyUserEvent().
 */
export default function useUsers({ keycloak, authenticated }) {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const timersRef = useRef(new Map());

  const getToken = useCallback(() => keycloak?.token, [keycloak]);

  const load = useCallback(async () => {
    if (!authenticated) return;
    setLoading(true);
    try {
      const data = await api.listUsers(getToken());
      setUsers((data || []).map((u) => ({ ...u, lastSyncAt: null, justUpdated: false })));
      setError(null);
    } catch (err) {
      setError(err.message || 'Failed to load users.');
    } finally {
      setLoading(false);
    }
  }, [authenticated, getToken]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(
    () => () => {
      timersRef.current.forEach((t) => clearTimeout(t));
      timersRef.current.clear();
    },
    []
  );

  const markJustUpdated = useCallback((id) => {
    const existingTimer = timersRef.current.get(id);
    if (existingTimer) clearTimeout(existingTimer);
    setUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, justUpdated: true, lastSyncAt: new Date().toISOString() } : u))
    );
    const timer = setTimeout(() => {
      setUsers((prev) => prev.map((u) => (u.id === id ? { ...u, justUpdated: false } : u)));
      timersRef.current.delete(id);
    }, JUST_UPDATED_MS);
    timersRef.current.set(id, timer);
  }, []);

  // Merge a live UserEvent (from the WS bridge) into local state.
  const applyUserEvent = useCallback(
    async (event) => {
      if (!event || !event.userId) return;
      const { eventType, userId } = event;

      if (eventType === 'USER_DELETED') {
        setUsers((prev) => prev.filter((u) => u.id !== userId));
        const t = timersRef.current.get(userId);
        if (t) {
          clearTimeout(t);
          timersRef.current.delete(userId);
        }
        return;
      }

      if (eventType === 'USER_CREATED' || eventType === 'USER_UPDATED') {
        try {
          // The event payload only carries username/email/realmRoles — re-fetch the
          // single user so the table also gets `enabled` and full name fields.
          const fresh = await api.getUser(userId, getToken());
          setUsers((prev) => {
            const exists = prev.some((u) => u.id === userId);
            if (exists) {
              return prev.map((u) => (u.id === userId ? { ...u, ...fresh } : u));
            }
            return [{ ...fresh, justUpdated: false, lastSyncAt: null }, ...prev];
          });
          markJustUpdated(userId);
        } catch (err) {
          // Single-user lookup failed (e.g. brief eventual-consistency lag) —
          // fall back to a full refetch rather than dropping the event silently.
          load();
        }
        return;
      }

      // Unknown eventType — refetch defensively so the table never silently drifts.
      load();
    },
    [getToken, load, markJustUpdated]
  );

  const create = useCallback(
    async (payload) => {
      const created = await api.createUser(payload, getToken());
      setUsers((prev) => [{ ...created, justUpdated: false, lastSyncAt: null }, ...prev]);
      return created;
    },
    [getToken]
  );

  const update = useCallback(
    async (id, payload) => {
      const updated = await api.updateUser(id, payload, getToken());
      setUsers((prev) => prev.map((u) => (u.id === id ? { ...u, ...updated } : u)));
      return updated;
    },
    [getToken]
  );

  const remove = useCallback(
    async (id) => {
      await api.deleteUser(id, getToken());
      setUsers((prev) => prev.filter((u) => u.id !== id));
    },
    [getToken]
  );

  return { users, loading, error, setError, refetch: load, applyUserEvent, create, update, remove };
}
