package com.redhat.service.bridge.manager.models;

public enum ProcessorWorkerStatus {
    CREATE_TOPIC_REQUESTED,
    TOPIC_CREATED,
    CREATE_CONNECTOR,
    CONNECTOR_READY,
    DELETE_TOPIC_REQUESTED,
    TOPIC_DELETED,
    DELETE_CONNECTOR,
    CONNECTOR_DELETED,
    FAILURE
}
