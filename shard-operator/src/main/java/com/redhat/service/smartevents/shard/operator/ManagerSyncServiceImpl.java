package com.redhat.service.smartevents.shard.operator;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.service.smartevents.shard.operator.reconcilers.BridgeReconciler;
import com.redhat.service.smartevents.shard.operator.reconcilers.ProcessorReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class ManagerSyncServiceImpl implements ManagerSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagerSyncServiceImpl.class);

    @Inject
    BridgeReconciler bridgeReconciler;

    @Inject
    ProcessorReconciler processorReconciler;

    @Override
    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void syncUpdatesFromManager() {
        LOGGER.info("Fetching updates from Manager for Bridges and Processors to deploy and delete");
        bridgeReconciler.reconcile();
        processorReconciler.reconcile();
    }
}
