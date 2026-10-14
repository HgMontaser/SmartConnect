import { useEffect } from 'react';
import { CloseIcon } from './icons.jsx';

export default function Modal({ title, subtitle, onClose, wide = false, children }) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const handleOverlayMouseDown = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className={`modal-card ${wide ? 'modal-card--wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-header">
          <div>
            <h2 className="modal-heading">{title}</h2>
            {subtitle && <p className="modal-subtitle">{subtitle}</p>}
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
            <CloseIcon />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
