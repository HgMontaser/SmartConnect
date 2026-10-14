import { Component } from 'react';
import { RefreshIcon } from './icons.jsx';
import { isChunkLoadError } from '../utils/lazyWithReload.js';

// Catches a page that failed to load or render so the shell stays usable. A missing chunk
// after a redeploy (when lazyWithReload's one automatic reload didn't fix it) gets a
// "new version" prompt; anything else gets a generic retry.
export default class PageErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error) {
    console.error('[backoffice-app] page failed:', error);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    const stale = isChunkLoadError(error);
    return (
      <div className={`page-error${this.props.fullScreen ? ' page-error--full' : ''}`} role="alert">
        <span className="page-error-icon">
          <RefreshIcon />
        </span>
        <h2 className="card-heading">{stale ? 'A new version is available' : 'This page failed to load'}</h2>
        <p className="card-subtext">
          {stale
            ? 'The backoffice was updated since you opened this tab. Reload to get the latest version.'
            : 'Something went wrong while showing this page. Reloading usually fixes it.'}
        </p>
        <button type="button" className="primary-button" onClick={() => window.location.reload()}>
          Reload
        </button>
      </div>
    );
  }
}
