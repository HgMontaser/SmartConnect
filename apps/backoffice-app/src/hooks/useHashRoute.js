import { useEffect, useState } from 'react';

function current() {
  return window.location.hash.replace(/^#\/?/, '') || '';
}

// Tiny hash router (#/users, #/roles, #/permissions) so a refresh keeps the current page
// without pulling in a routing library or touching the nginx SPA fallback.
export default function useHashRoute() {
  const [route, setRoute] = useState(current);

  useEffect(() => {
    const onChange = () => setRoute(current());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  const navigate = (next) => {
    window.location.hash = `/${next}`;
  };

  return [route, navigate];
}
