package com.redhat.service.bridge.infra.exceptions.definitions.user;

import javax.ws.rs.core.Response;

public class NotAuthorizedException extends ExternalUserException {

    private static final long serialVersionUID = 1L;

    public NotAuthorizedException(String message) {
        super(message);
    }

    public NotAuthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int getStatusCode() {
        return Response.Status.FORBIDDEN.getStatusCode();
    }
}