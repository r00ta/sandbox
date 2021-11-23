package com.redhat.service.bridge.shard.operator.controllers;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.service.bridge.infra.models.dto.BridgeStatus;
import com.redhat.service.bridge.infra.models.dto.ProcessorDTO;
import com.redhat.service.bridge.shard.operator.BridgeExecutorService;
import com.redhat.service.bridge.shard.operator.ManagerSyncService;
import com.redhat.service.bridge.shard.operator.resources.BridgeExecutor;
import com.redhat.service.bridge.shard.operator.resources.BridgeExecutorStatus;
import com.redhat.service.bridge.shard.operator.resources.PhaseType;
import com.redhat.service.bridge.shard.operator.watchers.ConfigMapEventSource;
import com.redhat.service.bridge.shard.operator.watchers.DeploymentEventSource;
import com.redhat.service.bridge.shard.operator.watchers.ServiceEventSource;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

@ApplicationScoped
@Controller
public class BridgeExecutorController implements ResourceController<BridgeExecutor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeExecutorController.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    ManagerSyncService managerSyncService;

    @Inject
    BridgeExecutorService bridgeExecutorService;

    @Override
    public void init(EventSourceManager eventSourceManager) {
        DeploymentEventSource deploymentEventSource = DeploymentEventSource.createAndRegisterWatch(kubernetesClient, BridgeExecutor.COMPONENT_NAME);
        eventSourceManager.registerEventSource("bridge-processor-deployment-event-source", deploymentEventSource);
        ServiceEventSource serviceEventSource = ServiceEventSource.createAndRegisterWatch(kubernetesClient, BridgeExecutor.COMPONENT_NAME);
        eventSourceManager.registerEventSource("bridge-processor-service-event-source", serviceEventSource);
        ConfigMapEventSource configMapEventSource = ConfigMapEventSource.createAndRegisterWatch(kubernetesClient, BridgeExecutor.COMPONENT_NAME);
        eventSourceManager.registerEventSource("bridge-processor-configmap-event-source", configMapEventSource);
    }

    @Override
    public UpdateControl<BridgeExecutor> createOrUpdateResource(BridgeExecutor bridgeExecutor, Context<BridgeExecutor> context) {
        LOGGER.debug("Create or update BridgeProcessor: '{}' in namespace '{}'", bridgeExecutor.getMetadata().getName(), bridgeExecutor.getMetadata().getNamespace());

        ConfigMap configMap = bridgeExecutorService.fetchOrCreateBridgeExecutorProcessorConfigMap(bridgeExecutor);

        Deployment deployment = bridgeExecutorService.fetchOrCreateBridgeExecutorDeployment(bridgeExecutor, configMap);
        if (!Readiness.isDeploymentReady(deployment)) {
            LOGGER.debug("Executor deployment BridgeProcessor: '{}' in namespace '{}' is NOT ready", bridgeExecutor.getMetadata().getName(),
                    bridgeExecutor.getMetadata().getNamespace());

            // TODO: Check if the deployment is in an error state, update the CRD and notify the manager!

            bridgeExecutor.setStatus(new BridgeExecutorStatus(PhaseType.AUGMENTATION));
            return UpdateControl.updateStatusSubResource(bridgeExecutor);
        }
        LOGGER.debug("Executor deployment BridgeProcessor: '{}' in namespace '{}' is ready", bridgeExecutor.getMetadata().getName(), bridgeExecutor.getMetadata().getNamespace());

        // Create Service
        Service service = bridgeExecutorService.fetchOrCreateBridgeExecutorService(bridgeExecutor, deployment);
        if (service.getStatus() == null) {
            LOGGER.debug("Executor service BridgeProcessor: '{}' in namespace '{}' is NOT ready", bridgeExecutor.getMetadata().getName(),
                    bridgeExecutor.getMetadata().getNamespace());
            bridgeExecutor.setStatus(new BridgeExecutorStatus(PhaseType.AUGMENTATION));
            return UpdateControl.updateStatusSubResource(bridgeExecutor);
        }
        LOGGER.debug("Executor service BridgeProcessor: '{}' in namespace '{}' is ready", bridgeExecutor.getMetadata().getName(), bridgeExecutor.getMetadata().getNamespace());

        if (!PhaseType.AVAILABLE.equals(bridgeExecutor.getStatus().getPhase())) {
            BridgeExecutorStatus bridgeExecutorStatus = new BridgeExecutorStatus(PhaseType.AVAILABLE);
            bridgeExecutor.setStatus(bridgeExecutorStatus);
            notifyManager(bridgeExecutor, BridgeStatus.AVAILABLE);
            return UpdateControl.updateStatusSubResource(bridgeExecutor);
        }
        return UpdateControl.noUpdate();
    }

    @Override
    public DeleteControl deleteResource(BridgeExecutor bridgeExecutor, Context<BridgeExecutor> context) {
        LOGGER.info("Deleted BridgeProcessor: '{}' in namespace '{}'", bridgeExecutor.getMetadata().getName(), bridgeExecutor.getMetadata().getNamespace());

        // Linked resources are automatically deleted

        notifyManager(bridgeExecutor, BridgeStatus.DELETED);

        return DeleteControl.DEFAULT_DELETE;
    }

    private void notifyManager(BridgeExecutor bridgeExecutor, BridgeStatus status) {
        ProcessorDTO dto = bridgeExecutor.toDTO();
        dto.setStatus(status);

        managerSyncService.notifyProcessorStatusChange(dto).subscribe().with(
                success -> LOGGER.info("[shard] Updating Processor with id '{}' done", dto.getId()),
                failure -> LOGGER.warn("[shard] Updating Processor with id '{}' FAILED", dto.getId()));
    }
}
