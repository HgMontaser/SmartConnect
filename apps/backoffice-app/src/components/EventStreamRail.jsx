import { timeAgo } from '../utils/time.js';

export default function EventStreamRail({ events }) {
  return (
    <aside className="event-rail">
      <div className="event-rail-header">
        <h2 className="event-rail-heading">Event stream</h2>
      </div>
      <div className="event-list">
        {events.length === 0 && (
          <p className="event-empty">No events yet. Live user changes will appear here.</p>
        )}
        {events.map((event) => (
          <div key={event.id} className="event-item">
            <div className="event-item-top">
              <span className="event-name">{event.displayName}</span>
              <span className="event-time">{timeAgo(event.occurredAt)}</span>
            </div>
            <p className="event-description">{event.description}</p>
          </div>
        ))}
      </div>
    </aside>
  );
}
