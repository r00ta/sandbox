package com.redhat.service.smartevents.manager.v2.api.user.models.requests;

import com.redhat.service.smartevents.manager.core.api.models.requests.BridgeRequest;
import com.redhat.service.smartevents.manager.core.api.validators.ValidCloudProvider;

@ValidCloudProvider
public class BridgeRequestV2 extends BridgeRequest {

    public BridgeRequestV2() {
    }

    public BridgeRequestV2(String name, String cloudProvider, String region) {
        super(name, cloudProvider, region);
    }
}