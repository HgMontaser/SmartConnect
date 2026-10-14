import { lazy } from 'react';

// A tab left open across a redeploy still runs the old bundle, which asks for hashed chunk
// files the new image no longer has. Reloading picks up the new index.html and its chunks.
// The timestamp in sessionStorage stops a loop if the chunk is still missing after reloading.
const RELOAD_KEY = 'backoffice:chunk-reload-at';
const RELOAD_GUARD_MS = 10_000;

// Chromium, Firefox and Safari word the failed dynamic import differently.
const CHUNK_ERROR = /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|Unable to preload CSS/i;

export function isChunkLoadError(error) {
  return CHUNK_ERROR.test(error?.message || '');
}

function readReloadAt() {
  try {
    return Number(sessionStorage.getItem(RELOAD_KEY)) || 0;
  } catch {
    return 0;
  }
}

function reloadOnce() {
  if (Date.now() - readReloadAt() < RELOAD_GUARD_MS) return false;
  try {
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()));
  } catch {
    // Without sessionStorage there's no loop guard - let the error boundary handle it instead.
    return false;
  }
  window.location.reload();
  return true;
}

export default function lazyWithReload(factory) {
  return lazy(() =>
    factory().catch((error) => {
      // Keep Suspense showing its spinner until the reload replaces the page.
      if (isChunkLoadError(error) && reloadOnce()) return new Promise(() => {});
      throw error;
    })
  );
}
