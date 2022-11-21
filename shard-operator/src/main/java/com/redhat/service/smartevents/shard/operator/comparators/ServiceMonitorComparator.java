package com.redhat.service.smartevents.shard.operator.comparators;

import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitor;

public class ServiceMonitorComparator implements Comparator<ServiceMonitor> {

    @Override
    public boolean compare(ServiceMonitor requestedResource, ServiceMonitor deployedResource) {
        return requestedResource.getSpec().equals(deployedResource.getSpec());
    }
}
