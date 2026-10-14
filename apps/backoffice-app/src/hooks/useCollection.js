import { useCallback, useEffect, useState } from 'react';

// Loads a list once on mount (and on reload()); `loader` must be a stable callback.
export default function useCollection(loader, { enabled = true } = {}) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(null);

  const reload = useCallback(async () => {
    if (!enabled) return;
    setLoading(true);
    try {
      setItems((await loader()) || []);
      setError(null);
    } catch (err) {
      setError(err.message || 'Failed to load.');
    } finally {
      setLoading(false);
    }
  }, [loader, enabled]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { items, setItems, loading, error, setError, reload };
}
