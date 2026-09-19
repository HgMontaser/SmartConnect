package com.smartconnect.commons.events;

/** Event types published by user-service to the {@code user-events} Kafka topic. */
public enum UserEventType {
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED
}
