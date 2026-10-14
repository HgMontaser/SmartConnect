import { useCallback, useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { SectionSpinner } from '../components/Spinner.jsx';
import LiveFeed from '../components/events/LiveFeed.jsx';
import TopicMessages from '../components/events/TopicMessages.jsx';
import TopicsTable from '../components/events/TopicsTable.jsx';
import ConsumersTable from '../components/events/ConsumersTable.jsx';
import MessageDrawer from '../components/events/MessageDrawer.jsx';
import TraceDrawer from '../components/TraceDrawer.jsx';
import usePolling from '../hooks/usePolling.js';
import * as api from '../api.js';
import { formatCount } from '../utils/kafka.js';

const TABS = [
  { id: 'messages', label: 'Messages' },
  { id: 'topics', label: 'Topics' },
  { id: 'consumers', label: 'Consumers' },
];
const LIVE = '__live';

// Kafka for the backoffice: every topic and its events with payloads and headers, dead
// letters, and the consumer groups reading them.
export default function EventsPage({ getToken, canTrace, liveEvents = [], connected = false }) {
  const [tab, setTab] = useState('messages');
  const [selected, setSelected] = useState(LIVE);
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState(null);
  const [tracing, setTracing] = useState(null);

  const loadTopics = useCallback(() => api.listTopics(getToken()), [getToken]);
  const { data: allTopics, error: topicsError, setError: setTopicsError } = usePolling(loadTopics, 15000);
  const loadGroups = useCallback(() => (tab === 'consumers' ? api.listConsumerGroups(getToken()) : Promise.resolve(null)), [tab, getToken]);
  const { data: groups, error: groupsError } = usePolling(loadGroups, tab === 'consumers' ? 10000 : 0);

  const topics = (allTopics ?? []).filter((t) => !t.internal);
  const topic = topics.find((t) => t.name === selected);
  const totalMessages = topics.reduce((n, t) => n + t.messages, 0);

  const browse = (name) => {
    setSelected(name);
    setTab('messages');
  };

  return (
    <>
      <PageHeader
        title="Events"
        subtitle="Every Kafka topic and the events on it - payloads, headers, dead letters - and the consumer groups reading them."
        search={tab === 'messages' ? search : undefined}
        onSearchChange={tab === 'messages' ? setSearch : undefined}
        searchPlaceholder="Search by person, action, service, event type or correlation id"
      />
      <div className="filter-bar">
        <div className="segmented" role="tablist">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={tab === t.id}
              className={`segmented-item ${tab === t.id ? 'segmented-item--active' : ''}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>
        {allTopics && (
          <span className="events-count">
            {topics.length} topics · {formatCount(totalMessages)} messages
          </span>
        )}
      </div>
      {topicsError && <ErrorBanner message={topicsError} onDismiss={() => setTopicsError(null)} />}

      {tab === 'messages' && (
        <div className="events-layout">
          <nav className="topic-list" aria-label="Topics">
            <button
              type="button"
              className={`topic-item ${selected === LIVE ? 'topic-item--active' : ''}`}
              onClick={() => setSelected(LIVE)}
            >
              <span className="topic-item-name">
                <span className={`live-dot ${connected ? 'live-dot--success' : 'live-dot--warn'}`} /> Live activity
              </span>
            </button>
            <span className="nav-section">Topics</span>
            {!allTopics && <SectionSpinner label="Loading topics…" />}
            {topics.map((t) => (
              <button
                key={t.name}
                type="button"
                className={`topic-item ${selected === t.name ? 'topic-item--active' : ''}`}
                onClick={() => setSelected(t.name)}
                title={t.name}
              >
                <span className="topic-item-name mono-cell">{t.name}</span>
                <span className="topic-item-meta">
                  {t.deadLetter && <span className="state-pill tone-bad topic-pill">DLT</span>}
                  <span className="nav-count">{formatCount(t.messages)}</span>
                </span>
              </button>
            ))}
          </nav>
          <div className="events-main">
            {selected === LIVE || !topic ? (
              <LiveFeed getToken={getToken} search={search} liveEvents={liveEvents} connected={connected} onOpen={setOpen} />
            ) : (
              <TopicMessages key={topic.name} topic={topic} getToken={getToken} search={search} onOpen={setOpen} />
            )}
          </div>
        </div>
      )}

      {tab === 'topics' && (allTopics ? <TopicsTable topics={topics} onBrowse={browse} /> : <SectionSpinner label="Loading topics…" />)}

      {tab === 'consumers' && (
        <>
          {groupsError && <div className="info-note">Couldn&apos;t load consumer groups: {groupsError}</div>}
          {groups ? <ConsumersTable groups={groups} /> : !groupsError && <SectionSpinner label="Loading consumer groups…" />}
        </>
      )}

      {open && (
        <MessageDrawer
          position={open}
          getToken={getToken}
          canTrace={canTrace}
          onTrace={(id) => {
            setOpen(null);
            setTracing(id);
          }}
          onClose={() => setOpen(null)}
        />
      )}
      {tracing && <TraceDrawer correlationId={tracing} getToken={getToken} onClose={() => setTracing(null)} />}
    </>
  );
}
