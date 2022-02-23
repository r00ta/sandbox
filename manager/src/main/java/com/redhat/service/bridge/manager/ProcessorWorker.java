package com.redhat.service.bridge.manager;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.openshift.cloud.api.connector.models.ConnectorRequest;
import com.openshift.cloud.api.connector.models.DeploymentLocation;
import com.openshift.cloud.api.connector.models.KafkaConnectionSettings;
import com.openshift.cloud.api.connector.models.ServiceAccount;
import com.redhat.service.bridge.infra.models.dto.ManagedEntityStatus;
import com.redhat.service.bridge.manager.connectors.ConnectorsApiClient;
import com.redhat.service.bridge.manager.dao.ConnectorsDAO;
import com.redhat.service.bridge.manager.dao.PreparingWorkerDAO;
import com.redhat.service.bridge.manager.dao.ProcessorDAO;
import com.redhat.service.bridge.manager.models.BridgeWorkerStatus;
import com.redhat.service.bridge.manager.models.ConnectorEntity;
import com.redhat.service.bridge.manager.models.PreparingWorker;
import com.redhat.service.bridge.manager.models.Processor;
import com.redhat.service.bridge.manager.models.ProcessorWorkerStatus;
import com.redhat.service.bridge.manager.providers.WorkerIdProvider;
import com.redhat.service.bridge.rhoas.RhoasTopicAccessType;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;

/**
 * Accepted -> Preparing -> topic requested -> topic created -> provisioning
 */

@ApplicationScoped
public class ProcessorWorker implements AbstractWorker<Processor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorWorker.class);

    private static final String CREATE_KAFKA_TOPIC = "processor-create-topic";
    private static final String CREATE_CONNECTOR = "processor-create-connector";
    private static final String CONNECTOR_READY = "processor-ready-connector";
    private static final String DELETE_KAFKA_TOPIC = "processor-delete-topic";
    private static final String DELETE_CONNECTOR = "processor-delete-connector";

    public static final String KAFKA_ID_IGNORED = "kafkaId-ignored";

    @ConfigProperty(name = "managed-connectors.cluster.id")
    String mcClusterId;

    @ConfigProperty(name = "managed-connectors.kafka.bootstrap.servers")
    String kafkaBootstrapServer;

    @ConfigProperty(name = "managed-connectors.kafka.client.id")
    String serviceAccountId;

    @ConfigProperty(name = "managed-connectors.kafka.client.secret")
    String serviceAccountSecret;

    @Inject
    ConnectorsApiClient connectorsApiClient;

    @Inject
    EventBus eventBus;

    @Inject
    ProcessorDAO processorDAO;

    @Inject
    ConnectorsDAO connectorsDAO;

    @Inject
    PreparingWorkerDAO preparingWorkerDAO;

    @Inject
    RhoasService rhoasService;

    @Inject
    WorkerIdProvider workerIdProvider;

    @Override
    public void accept(Processor processor) {
        LOGGER.info("Accept entity " + processor.getId());

        PreparingWorker preparingWorker = new PreparingWorker();
        preparingWorker.setEntityId(processor.getId());
        preparingWorker.setStatus(null);
        preparingWorker.setDesiredStatus(ProcessorWorkerStatus.TOPIC_CREATED.toString()); // where you want to go. i.e. step 1
        preparingWorker.setWorkerId(workerIdProvider.getWorkerId());
        preparingWorker.setSubmittedAt(ZonedDateTime.now(ZoneOffset.UTC));
        preparingWorker.setModifiedAt(ZonedDateTime.now(ZoneOffset.UTC));
        preparingWorker.setType("PROCESSOR");
        transact(preparingWorker);

        routePreparingWorker(preparingWorker, processor);
    }

    @Override
    public void deprovision(Processor processor) {
        LOGGER.info("Deprovision entity " + processor.getId());

        processor.setStatus(ManagedEntityStatus.DEPROVISION_ACCEPTED);
        processor.setDesiredStatus(ManagedEntityStatus.DEPROVISION);

        // Persist and fire
        transact(processor);

        PreparingWorker preparingWorker = new PreparingWorker();
        preparingWorker.setEntityId(processor.getId());
        preparingWorker.setStatus(null);
        preparingWorker.setDesiredStatus(BridgeWorkerStatus.TOPIC_DELETED.toString()); // where you want to go. i.e. step 1
        preparingWorker.setWorkerId(workerIdProvider.getWorkerId());
        preparingWorker.setSubmittedAt(ZonedDateTime.now(ZoneOffset.UTC));
        preparingWorker.setModifiedAt(ZonedDateTime.now(ZoneOffset.UTC));
        transact(preparingWorker);

        routePreparingWorker(preparingWorker, processor);
    }

    @ConsumeEvent(value = CREATE_KAFKA_TOPIC, blocking = true)
    public void step1_createKafkaTopic(Processor processor) {
        ConnectorEntity connectorEntity = connectorsDAO.findByProcessorId(processor.getId()).get(0);
        try {
            rhoasService.createTopicAndGrantAccessFor(connectorEntity.getTopicName(), RhoasTopicAccessType.PRODUCER);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to create the topic. The retry will be performed by the reschedule loop.", e);
            updatePreparingWorker(processor.getId(), ProcessorWorkerStatus.FAILURE);
            return;
        }

        PreparingWorker preparingWorker = updatePreparingWorker(processor.getId(), ProcessorWorkerStatus.TOPIC_CREATED, ProcessorWorkerStatus.CREATE_CONNECTOR);
        routePreparingWorker(preparingWorker, processor);
    }

    @ConsumeEvent(value = CREATE_CONNECTOR, blocking = true)
    public void step2_createConnector(Processor processor) {
        ConnectorEntity connectorEntity = connectorsDAO.findByProcessorId(processor.getId()).get(0);
        JsonNode payload = connectorEntity.getDefinition();
        String newConnectorName = connectorEntity.getName();
        String connectorType = connectorEntity.getConnectorType();

        ConnectorRequest createConnectorRequest = new ConnectorRequest();

        createConnectorRequest.setName(newConnectorName);

        DeploymentLocation deploymentLocation = new DeploymentLocation();
        deploymentLocation.setKind("addon");
        deploymentLocation.setClusterId(mcClusterId);
        createConnectorRequest.setDeploymentLocation(deploymentLocation);

        createConnectorRequest.setConnectorTypeId(connectorType);

        createConnectorRequest.setConnector(payload);

        ServiceAccount serviceAccount = new ServiceAccount();
        serviceAccount.setClientId(serviceAccountId);
        serviceAccount.setClientSecret(serviceAccountSecret);
        createConnectorRequest.setServiceAccount(serviceAccount);

        KafkaConnectionSettings kafka = new KafkaConnectionSettings();
        kafka.setUrl(kafkaBootstrapServer);

        // https://issues.redhat.com/browse/MGDOBR-198
        // this is currently ignored in the Connectors API
        kafka.setId(KAFKA_ID_IGNORED);

        createConnectorRequest.setKafka(kafka);

        try {
            connectorsApiClient.createConnector(createConnectorRequest);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to create the connector. The retry will be performed by the reschedule loop.", e);
            updatePreparingWorker(processor.getId(), ProcessorWorkerStatus.FAILURE);
            return;
        }

        PreparingWorker preparingWorker = updatePreparingWorker(processor.getId(), ProcessorWorkerStatus.CREATE_CONNECTOR, ProcessorWorkerStatus.CONNECTOR_READY);
        routePreparingWorker(preparingWorker, processor);
    }

    @ConsumeEvent(value = CONNECTOR_READY, blocking = true)
    public void step3_readyConnector(Processor processor) {
        /**
         * if (!connectorsApi.isConnectorReady()){
         * LOGGER.error("Connector is not ready. The retry will be performed by the reschedule loop.", e);
         * PreparingWorker preparingWorker = preparingWorkerDAO.findByEntityId(processor.getId()).get(0);
         * preparingWorker.setModifiedAt(ZonedDateTime.now(ZoneOffset.UTC));
         * preparingWorker.setStatus(BridgeWorkerStatus.FAILURE.toString()); // where you are. The rescheduler will fire the event according to the desiredStatus
         * transact(preparingWorker);
         * }
         */
        LOGGER.info("Connector is ready, ready to be provisioned by shard.");
        PreparingWorker preparingWorker = preparingWorkerDAO.findByEntityId(processor.getId()).get(0);
        preparingWorkerDAO.deleteById(preparingWorker.getId());

        // The bridge is ready to be provisioned by the shard.
        processor.setStatus(ManagedEntityStatus.PREPARING);
        processor.setDesiredStatus(ManagedEntityStatus.PROVISIONING);
        transact(processor);
    }

    @Transactional
    @ConsumeEvent(value = DELETE_KAFKA_TOPIC, blocking = true)
    public void step1_deleteKafkaTopic(Processor processor) {
        // TODO
    }

    @Override
    @Scheduled(every = "30s")
    public void reschedule() {
        LOGGER.info("resched");
        List<PreparingWorker> preparingWorkers = preparingWorkerDAO.findByWorkerIdAndStatusAndType(workerIdProvider.getWorkerId(), BridgeWorkerStatus.FAILURE.toString(), "PROCESSOR");
        for (PreparingWorker preparingWorker : preparingWorkers) {
            Processor processor = processorDAO.findById(preparingWorker.getEntityId());
            routePreparingWorker(preparingWorker, processor);
        }
    }

    @Override
    @Scheduled(every = "5m")
    public void discoverOrphanWorkers() {
        LOGGER.info("discovering orphan");
        ZonedDateTime orphans = ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(30); // entities older than 30 minutes will be processed with an override of the worker_id.
        List<PreparingWorker> preparingWorkers = preparingWorkerDAO.findByAgeAndStatusAndType(orphans, BridgeWorkerStatus.FAILURE.toString(), "PROCESSOR");
        for (PreparingWorker preparingWorker : preparingWorkers) {

            preparingWorker.setWorkerId(workerIdProvider.getWorkerId());
            transact(preparingWorker);

            Processor processor = processorDAO.findById(preparingWorker.getEntityId());
            routePreparingWorker(preparingWorker, processor);
        }
    }

    @Transactional
    private void transact(Processor b) {
        processorDAO.getEntityManager().merge(b);
    }

    @Transactional
    private void transact(PreparingWorker b) {
        preparingWorkerDAO.getEntityManager().merge(b);
    }

    @Transactional
    private PreparingWorker updatePreparingWorker(String processorId, ProcessorWorkerStatus status, ProcessorWorkerStatus desiredStatus) {
        PreparingWorker preparingWorker = preparingWorkerDAO.findByEntityId(processorId).get(0);
        preparingWorker.setModifiedAt(ZonedDateTime.now(ZoneOffset.UTC));
        preparingWorker.setStatus(status.toString());
        preparingWorker.setDesiredStatus(desiredStatus.toString());
        return preparingWorker;
    }

    @Transactional
    private PreparingWorker updatePreparingWorker(String processorId, ProcessorWorkerStatus status) {
        PreparingWorker preparingWorker = preparingWorkerDAO.findByEntityId(processorId).get(0);
        preparingWorker.setModifiedAt(ZonedDateTime.now(ZoneOffset.UTC));
        preparingWorker.setStatus(status.toString());
        return preparingWorker;
    }

    private void routePreparingWorker(PreparingWorker preparingWorker, Processor processor) {
        switch (ProcessorWorkerStatus.valueOf(preparingWorker.getDesiredStatus())) {
            case CREATE_TOPIC_REQUESTED:
                eventBus.requestAndForget(CREATE_KAFKA_TOPIC, processor);
                break;
            case CREATE_CONNECTOR:
                eventBus.requestAndForget(CREATE_CONNECTOR, processor);
                break;
            case CONNECTOR_READY:
                eventBus.requestAndForget(CONNECTOR_READY, processor);
                break;
            case DELETE_TOPIC_REQUESTED:
                eventBus.requestAndForget(DELETE_KAFKA_TOPIC, processor);
                break;
            default:
                LOGGER.error("ProcessorPreparingWorker can't process failed worker with desired status " + preparingWorker.getDesiredStatus());
        }
    }

}
