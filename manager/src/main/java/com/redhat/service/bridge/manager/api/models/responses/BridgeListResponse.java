package com.redhat.service.bridge.manager.api.models.responses;

import com.redhat.service.bridge.infra.api.models.responses.ListResponse;

public class BridgeListResponse extends ListResponse<BridgeResponse> {

    public BridgeListResponse() {
        super("BridgeList");
    }

}
