package com.redhat.service.bridge.manager.exceptions;

import javax.ws.rs.core.Response;

public class AlreadyExistingItemException extends EventBridgeManagerException {

    private static final long serialVersionUID = 1L;

    public AlreadyExistingItemException(String message) {
        super(message);
    }

    public AlreadyExistingItemException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int getStatusCode() {
        return Response.Status.BAD_REQUEST.getStatusCode();
    }
}