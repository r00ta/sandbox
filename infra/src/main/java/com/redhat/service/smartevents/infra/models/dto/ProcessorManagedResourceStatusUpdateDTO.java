package com.redhat.service.smartevents.infra.models.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessorManagedResourceStatusUpdateDTO extends ManagedResourceStatusUpdateDTO {

    @JsonProperty("bridgeId")
    private String bridgeId;

    public ProcessorManagedResourceStatusUpdateDTO() {
    }

    public ProcessorManagedResourceStatusUpdateDTO(String id, String customerId, String bridgeId, ManagedResourceStatus status) {
        super(id, customerId, status);
        this.bridgeId = bridgeId;
    }

    public String getBridgeId() {
        return bridgeId;
    }

    public void setBridgeId(String bridgeId) {
        this.bridgeId = bridgeId;
    }
}