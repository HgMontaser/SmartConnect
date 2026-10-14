import { useCallback, useEffect, useRef, useState } from 'react';

// Loads once, then every `intervalMs` while the page is visible. `loader` must be stable.
export default function usePolling(loader, intervalMs) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const inFlight = useRef(false);

  const refresh = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    try {
      setData(await loader());
      setError(null);
    } catch (err) {
      setError(err.message || 'Failed to load.');
    } finally {
      inFlight.current = false;
      setLoading(false);
    }
  }, [loader]);

  useEffect(() => {
    refresh();
    if (!intervalMs) return undefined;
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') refresh();
    }, intervalMs);
    return () => clearInterval(timer);
  }, [refresh, intervalMs]);

  return { data, error, setError, loading, refresh };
}
