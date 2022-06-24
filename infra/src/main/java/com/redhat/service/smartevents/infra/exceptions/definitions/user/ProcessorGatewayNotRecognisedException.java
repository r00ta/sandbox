package com.redhat.service.smartevents.infra.exceptions.definitions.user;

import javax.ws.rs.core.Response;

public class ProcessorGatewayNotRecognisedException extends ExternalUserException {

    private static final long serialVersionUID = 1L;

    public ProcessorGatewayNotRecognisedException(String message) {
        super(message);
    }

    @Override
    public int getStatusCode() {
        return Response.Status.BAD_REQUEST.getStatusCode();
    }
}
