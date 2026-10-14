package com.smartconnect.commons.messaging;

/**
 * How a service consumes events:
 * <pre>
 * &#64;KafkaListener(topics = "events.billing", containerFactory = EventListeners.FACTORY)
 * void on(EventEnvelope event) {
 *     if (event.type().equals("billing.invoice-paid")) { ... event.payloadAs(InvoicePaid.class) ... }
 * }
 * </pre>
 * The factory gives every listener: a consumer group named after the service (each service
 * gets every event once), JSON envelope decoding, tracing, correlation ids, and failure
 * handling - a throwing listener is retried {@code 3} times with backoff, then the record is
 * parked on the service's dead-letter topic ({@link EventTopics#deadLetter}) with the error in
 * its headers, so one bad message never blocks the ones behind it. Messages that can't even be
 * decoded go straight to the dead-letter topic.
 */
public final class EventListeners {

    public static final String FACTORY = "smartconnectEventListenerFactory";

    private EventListeners() {
    }
}
