import { useCallback, useEffect, useRef, useState } from 'react';

const INITIAL_BACKOFF_MS = 2000;
const MAX_BACKOFF_MS = 30000;

const GATEWAY_WS_URL = import.meta.env.VITE_GATEWAY_WS_URL || 'ws://localhost:8090';

/**
 * Single WebSocket connection to the gateway's user-events bridge. This is the
 * ONLY live-data source in the app — both the Users table's "Last sync" indicator
 * and the Event stream rail are driven off the same onEvent callback from here,
 * not two separate connections/polling loops.
 *
 * The access token is a query param (browsers can't set custom headers on the WS
 * handshake), and it's rebuilt fresh from keycloak.token every time we (re)connect,
 * so a token refreshed via keycloak.updateToken() is picked up on the next connect
 * rather than a stale token being reused forever.
 */
export default function useUserEventsSocket({ keycloak, authenticated, onEvent }) {
  const [connected, setConnected] = useState(false);
  const wsRef = useRef(null);
  const backoffRef = useRef(INITIAL_BACKOFF_MS);
  const reconnectTimerRef = useRef(null);
  const unmountedRef = useRef(false);
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const connect = useCallback(() => {
    if (unmountedRef.current) return;
    if (!authenticated || !keycloak?.token) return;
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }

    const url = `${GATEWAY_WS_URL}/ws/events?access_token=${encodeURIComponent(keycloak.token)}`;
    let socket;
    try {
      socket = new WebSocket(url);
    } catch (err) {
      // Constructing a WebSocket can throw synchronously on a malformed URL —
      // treat it the same as a dropped connection and retry with backoff.
      scheduleReconnect();
      return;
    }
    wsRef.current = socket;

    socket.onopen = () => {
      backoffRef.current = INITIAL_BACKOFF_MS;
      setConnected(true);
    };

    socket.onmessage = (message) => {
      try {
        const parsed = JSON.parse(message.data);
        onEventRef.current?.(parsed);
      } catch (err) {
        // Ignore malformed frames rather than crashing the UI.
        // eslint-disable-next-line no-console
        console.warn('Ignoring malformed WS event frame', err);
      }
    };

    function scheduleReconnect() {
      setConnected(false);
      wsRef.current = null;
      if (unmountedRef.current) return;
      const delay = backoffRef.current;
      backoffRef.current = Math.min(backoffRef.current * 2, MAX_BACKOFF_MS);
      reconnectTimerRef.current = setTimeout(connect, delay);
    }

    socket.onclose = scheduleReconnect;
    socket.onerror = () => {
      // onclose fires right after onerror for a failed connection, which will
      // schedule the reconnect — just make sure the socket actually closes.
      socket.close();
    };
  }, [authenticated, keycloak]);

  useEffect(() => {
    unmountedRef.current = false;
    connect();
    return () => {
      unmountedRef.current = true;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.onerror = null;
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [connect]);

  return { connected };
}
