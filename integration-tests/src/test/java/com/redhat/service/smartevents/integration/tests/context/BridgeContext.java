package com.redhat.service.smartevents.integration.tests.context;

import java.util.HashMap;
import java.util.Map;

import io.cucumber.java.Scenario;

/**
 * Shared bridge context
 */
public class BridgeContext {

    private String name;
    private String id;

    private Map<String, ProcessorContext> processors = new HashMap<>();

    private String endPoint;

    private boolean deleted;

    private Scenario scenario;

    public BridgeContext(Scenario scenario, String id, String systemBridgeName) {
        this.scenario = scenario;
        this.id = id;
        this.name = systemBridgeName;
    }

    public String getName() {
        return this.name;
    }

    public String getId() {
        return id;
    }

    public String getEndPoint() {
        return this.endPoint;
    }

    public void setEndPoint(String endPoint) {
        this.endPoint = endPoint;
    }

    public ProcessorContext newProcessor(String processorName, String processorId) {
        scenario.log("Bridge '" + this.name + "': Creating new Processor context with name '" + processorName + "' and id '"
                + processorId + "'");
        ProcessorContext processorContext = new ProcessorContext(this.scenario, processorId);
        this.processors.put(processorName, processorContext);
        return processorContext;
    }

    public void removeProcessor(String processorName) {
        this.processors.get(processorName).setDeleted(true);
    }

    public ProcessorContext getProcessor(String processorName) {
        if (!this.processors.containsKey(processorName)) {
            throw new RuntimeException("Processor with name " + processorName + " does not exist in bridge context.");
        }
        return this.processors.get(processorName);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
