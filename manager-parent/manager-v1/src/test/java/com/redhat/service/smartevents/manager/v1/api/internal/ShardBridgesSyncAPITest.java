package com.redhat.service.smartevents.manager.v1.api.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.redhat.service.smartevents.infra.core.api.dto.KafkaConnectionDTO;
import com.redhat.service.smartevents.infra.core.models.ManagedResourceStatus;
import com.redhat.service.smartevents.infra.v1.api.models.dto.BridgeDTO;
import com.redhat.service.smartevents.infra.v1.api.models.dto.ProcessorDTO;
import com.redhat.service.smartevents.infra.v1.api.models.filters.BaseFilter;
import com.redhat.service.smartevents.infra.v1.api.models.filters.StringEquals;
import com.redhat.service.smartevents.infra.v1.api.models.gateways.Action;
import com.redhat.service.smartevents.manager.core.dns.DnsService;
import com.redhat.service.smartevents.manager.core.services.RhoasService;
import com.redhat.service.smartevents.manager.v1.TestConstants;
import com.redhat.service.smartevents.manager.v1.api.models.requests.BridgeRequestV1;
import com.redhat.service.smartevents.manager.v1.api.models.requests.ProcessorRequest;
import com.redhat.service.smartevents.manager.v1.api.models.responses.BridgeResponse;
import com.redhat.service.smartevents.manager.v1.utils.DatabaseManagerUtils;
import com.redhat.service.smartevents.manager.v1.utils.TestUtils;
import com.redhat.service.smartevents.processor.actions.kafkatopic.KafkaTopicAction;
import com.redhat.service.smartevents.processor.actions.sendtobridge.SendToBridgeAction;
import com.redhat.service.smartevents.processor.actions.webhook.WebhookAction;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;

import static com.redhat.service.smartevents.infra.core.api.APIConstants.ACCOUNT_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM;
import static com.redhat.service.smartevents.infra.core.api.APIConstants.ORG_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM;
import static com.redhat.service.smartevents.infra.core.api.APIConstants.USER_NAME_ATTRIBUTE_CLAIM;
import static com.redhat.service.smartevents.infra.core.models.ManagedResourceStatus.DELETED;
import static com.redhat.service.smartevents.infra.core.models.ManagedResourceStatus.DEPROVISION;
import static com.redhat.service.smartevents.infra.core.models.ManagedResourceStatus.FAILED;
import static com.redhat.service.smartevents.infra.core.models.ManagedResourceStatus.PREPARING;
import static com.redhat.service.smartevents.infra.core.models.ManagedResourceStatus.PROVISIONING;
import static com.redhat.service.smartevents.infra.core.models.ManagedResourceStatus.READY;
import static com.redhat.service.smartevents.manager.v1.TestConstants.DEFAULT_BRIDGE_NAME;
import static com.redhat.service.smartevents.manager.v1.TestConstants.DEFAULT_CLOUD_PROVIDER;
import static com.redhat.service.smartevents.manager.v1.TestConstants.DEFAULT_CUSTOMER_ID;
import static com.redhat.service.smartevents.manager.v1.TestConstants.DEFAULT_ORGANISATION_ID;
import static com.redhat.service.smartevents.manager.v1.TestConstants.DEFAULT_PROCESSOR_NAME;
import static com.redhat.service.smartevents.manager.v1.TestConstants.DEFAULT_REGION;
import static com.redhat.service.smartevents.manager.v1.TestConstants.DEFAULT_USER_NAME;
import static com.redhat.service.smartevents.manager.v1.TestConstants.SHARD_ID;
import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@QuarkusTest
public class ShardBridgesSyncAPITest {

    private static final String TEST_BRIDGE_TLS_CERTIFICATE = "certificate";
    private static final String TEST_BRIDGE_TLS_KEY = "key";

    @Inject
    DatabaseManagerUtils databaseManagerUtils;

    @Inject
    DnsService dnsService;

    @InjectMock
    JsonWebToken jwt;

    @InjectMock
    RhoasService rhoasServiceMock;

    @ConfigProperty(name = "rhose.metrics-name.operation-total-count")
    String operationTotalCountMetricName;

    @ConfigProperty(name = "rhose.metrics-name.operation-success-total-count")
    String operationTotalSuccessCountMetricName;

    @ConfigProperty(name = "rhose.metrics-name.operation-failure-total-count")
    String operationTotalFailureCountMetricName;

    @ConfigProperty(name = "rhose.metrics-name.operation-duration-seconds")
    String operationDurationMetricName;

    @BeforeEach
    public void cleanUp() {
        databaseManagerUtils.cleanUpAndInitWithDefaultShard();
        when(jwt.getClaim(ACCOUNT_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM)).thenReturn(SHARD_ID);
        when(jwt.containsClaim(ACCOUNT_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM)).thenReturn(true);
        when(jwt.getClaim(ORG_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM)).thenReturn(DEFAULT_ORGANISATION_ID);
        when(jwt.containsClaim(ORG_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM)).thenReturn(true);
        when(jwt.getClaim(USER_NAME_ATTRIBUTE_CLAIM)).thenReturn(DEFAULT_USER_NAME);
        when(jwt.containsClaim(USER_NAME_ATTRIBUTE_CLAIM)).thenReturn(true);
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void getProcessorsWithKafkaAction() {
        BridgeResponse bridgeResponse = TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION)).as(BridgeResponse.class);
        //Emulate the Shard having deployed the Bridge
        BridgeDTO bridge = new BridgeDTO(bridgeResponse.getId(),
                bridgeResponse.getName(),
                TestConstants.DEFAULT_BRIDGE_ENDPOINT,
                TEST_BRIDGE_TLS_CERTIFICATE,
                TEST_BRIDGE_TLS_KEY,
                DEFAULT_CUSTOMER_ID,
                DEFAULT_USER_NAME,
                READY,
                new KafkaConnectionDTO());
        TestUtils.updateBridge(bridge);

        //Create a Processor for the Bridge
        Set<BaseFilter> filters = Collections.singleton(new StringEquals("json.key", "value"));
        TestUtils.addProcessorToBridge(bridgeResponse.getId(), new ProcessorRequest(DEFAULT_PROCESSOR_NAME, filters, null, TestUtils.createKafkaAction()));

        final List<ProcessorDTO> processors = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            processors.clear();
            processors.addAll(TestUtils.getProcessorsToDeployOrDelete().as(new TypeRef<List<ProcessorDTO>>() {
            }));
            assertThat(processors.size()).isEqualTo(1);
        });

        ProcessorDTO processor = processors.get(0);
        assertThat(processor.getName()).isEqualTo(DEFAULT_PROCESSOR_NAME);
        assertThat(processor.getStatus()).isEqualTo(PREPARING);
        assertThat(processor.getDefinition().getFilters().size()).isEqualTo(1);
        assertThat(processor.getDefinition().getRequestedAction()).isNotNull();
        assertThat(processor.getDefinition().getRequestedAction().getType()).isEqualTo(KafkaTopicAction.TYPE);
        assertThat(processor.getDefinition().getRequestedAction().getParameter(KafkaTopicAction.TOPIC_PARAM)).isEqualTo(TestConstants.DEFAULT_KAFKA_TOPIC);
        assertThat(processor.getDefinition().getResolvedAction()).isNotNull();
        assertThat(processor.getDefinition().getResolvedAction().getType()).isEqualTo(KafkaTopicAction.TYPE);
        assertThat(processor.getDefinition().getResolvedAction().getParameter(KafkaTopicAction.TOPIC_PARAM)).isEqualTo(TestConstants.DEFAULT_KAFKA_TOPIC);
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void getProcessorsWithSendToBridgeAction() {
        BridgeResponse bridgeResponse = TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION)).as(BridgeResponse.class);
        String bridgeId = bridgeResponse.getId();
        String endpoint = dnsService.buildBridgeEndpoint(bridgeResponse.getId(), DEFAULT_CUSTOMER_ID);
        //Emulate the Shard having deployed the Bridge
        BridgeDTO bridge = new BridgeDTO(bridgeId,
                bridgeResponse.getName(),
                endpoint,
                TEST_BRIDGE_TLS_CERTIFICATE,
                TEST_BRIDGE_TLS_KEY,
                DEFAULT_CUSTOMER_ID,
                DEFAULT_USER_NAME,
                READY,
                new KafkaConnectionDTO());
        TestUtils.updateBridge(bridge);

        //Create a Processor for the Bridge
        Set<BaseFilter> filters = Collections.singleton(new StringEquals("json.key", "value"));
        Action action = TestUtils.createSendToBridgeAction(bridgeId);
        TestUtils.addProcessorToBridge(bridgeResponse.getId(), new ProcessorRequest(DEFAULT_PROCESSOR_NAME, filters, null, action));

        final List<ProcessorDTO> processors = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            processors.clear();
            processors.addAll(TestUtils.getProcessorsToDeployOrDelete().as(new TypeRef<List<ProcessorDTO>>() {
            }));
            assertThat(processors.size()).isEqualTo(1);
        });

        ProcessorDTO processor = processors.get(0);
        assertThat(processor.getName()).isEqualTo(DEFAULT_PROCESSOR_NAME);
        assertThat(processor.getStatus()).isEqualTo(PREPARING);
        assertThat(processor.getDefinition().getFilters().size()).isEqualTo(1);
        assertThat(processor.getDefinition().getRequestedAction()).isNotNull();
        assertThat(processor.getDefinition().getRequestedAction().getType()).isEqualTo(SendToBridgeAction.TYPE);
        assertThat(processor.getDefinition().getRequestedAction().getParameter(SendToBridgeAction.BRIDGE_ID_PARAM)).isEqualTo(bridgeId);
        assertThat(processor.getDefinition().getResolvedAction()).isNotNull();
        assertThat(processor.getDefinition().getResolvedAction().getType()).isEqualTo(WebhookAction.TYPE);
        assertThat(processor.getDefinition().getResolvedAction().getParameter(WebhookAction.ENDPOINT_PARAM)).isEqualTo(endpoint);
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void updateProcessorStatus() {
        BridgeResponse bridgeResponse = TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION)).as(BridgeResponse.class);
        BridgeDTO bridge = new BridgeDTO(bridgeResponse.getId(),
                bridgeResponse.getName(),
                TestConstants.DEFAULT_BRIDGE_ENDPOINT,
                TEST_BRIDGE_TLS_CERTIFICATE,
                TEST_BRIDGE_TLS_KEY,

                DEFAULT_CUSTOMER_ID,
                DEFAULT_USER_NAME,
                READY,
                new KafkaConnectionDTO());
        TestUtils.updateBridge(bridge);
        TestUtils.addProcessorToBridge(bridgeResponse.getId(), new ProcessorRequest(DEFAULT_PROCESSOR_NAME, TestUtils.createKafkaAction()));

        final List<ProcessorDTO> processors = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            processors.clear();
            processors.addAll(TestUtils.getProcessorsToDeployOrDelete().as(new TypeRef<List<ProcessorDTO>>() {
            }));
            assertThat(processors.size()).isEqualTo(1);
        });

        ProcessorDTO processor = processors.get(0);
        processor.setStatus(READY);

        TestUtils.updateProcessor(processor);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            processors.clear();
            processors.addAll(TestUtils.getProcessorsToDeployOrDelete().as(new TypeRef<List<ProcessorDTO>>() {
            }));
            assertThat(processors).isEmpty();
        });
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void metricsAreProducedForSuccessfulDeployment() {
        BridgeResponse bridgeResponse = TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION)).as(BridgeResponse.class);
        BridgeDTO bridge = new BridgeDTO(bridgeResponse.getId(),
                bridgeResponse.getName(),
                TestConstants.DEFAULT_BRIDGE_ENDPOINT,
                TEST_BRIDGE_TLS_CERTIFICATE,
                TEST_BRIDGE_TLS_KEY,
                DEFAULT_CUSTOMER_ID,
                DEFAULT_USER_NAME,
                READY,
                new KafkaConnectionDTO());
        TestUtils.updateBridge(bridge);
        TestUtils.addProcessorToBridge(bridgeResponse.getId(), new ProcessorRequest(DEFAULT_PROCESSOR_NAME, TestUtils.createKafkaAction()));

        final List<ProcessorDTO> processors = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            processors.clear();
            processors.addAll(TestUtils.getProcessorsToDeployOrDelete().as(new TypeRef<List<ProcessorDTO>>() {
            }));
            assertThat(processors.size()).isEqualTo(1);
        });

        ProcessorDTO processor = processors.get(0);
        processor.setStatus(READY);

        TestUtils.updateProcessor(processor);

        String metrics = given()
                .filter(new ResponseLoggingFilter())
                .contentType(ContentType.JSON)
                .when()
                .get("/q/metrics")
                .then()
                .extract()
                .body()
                .asString();

        // Not all metrics are recorded by this test. Only the "successful" path in this test.
        assertThat(metrics).contains(operationTotalCountMetricName);
        assertThat(metrics).contains(operationTotalSuccessCountMetricName);
        assertThat(metrics).contains(operationDurationMetricName);
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void metricsAreProducedForFailedDeployment() {
        BridgeResponse bridgeResponse = TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION)).as(BridgeResponse.class);
        BridgeDTO bridge = new BridgeDTO(bridgeResponse.getId(),
                bridgeResponse.getName(),
                TestConstants.DEFAULT_BRIDGE_ENDPOINT,
                TEST_BRIDGE_TLS_CERTIFICATE,
                TEST_BRIDGE_TLS_KEY,
                DEFAULT_CUSTOMER_ID,
                DEFAULT_USER_NAME,
                READY,
                new KafkaConnectionDTO());
        TestUtils.updateBridge(bridge);
        Action action = TestUtils.createWebhookAction();
        action.setMapParameters(Map.of(WebhookAction.ENDPOINT_PARAM, "https://webhook.site/${env.webhook.site.uuid}"));
        TestUtils.addProcessorToBridge(bridgeResponse.getId(), new ProcessorRequest(DEFAULT_PROCESSOR_NAME, action));

        final List<ProcessorDTO> processors = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            processors.clear();
            processors.addAll(TestUtils.getProcessorsToDeployOrDelete().as(new TypeRef<List<ProcessorDTO>>() {
            }));
            assertThat(processors.size()).isEqualTo(1);
        });

        ProcessorDTO processor = processors.get(0);
        processor.setStatus(FAILED);

        TestUtils.updateProcessor(processor);

        String metrics = given()
                .filter(new ResponseLoggingFilter())
                .contentType(ContentType.JSON)
                .when()
                .get("/q/metrics")
                .then()
                .extract()
                .body()
                .asString();

        // Not all metrics are recorded by this test. Only the "failure" path in this test.
        assertThat(metrics).contains(operationTotalCountMetricName);
        assertThat(metrics).contains(operationTotalFailureCountMetricName);
        assertThat(metrics).contains(operationDurationMetricName);
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void testGetEmptyBridgesToDeploy() {
        final List<BridgeDTO> bridgesToDeployOrDelete = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            bridgesToDeployOrDelete.clear();
            bridgesToDeployOrDelete.addAll(TestUtils.getBridgesToDeployOrDelete().as(new TypeRef<List<BridgeDTO>>() {
            }));
            assertThat(bridgesToDeployOrDelete).isEmpty();
        });
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void testGetBridgesToDeploy() {
        TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION));

        final List<BridgeDTO> bridgesToDeployOrDelete = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            bridgesToDeployOrDelete.clear();
            bridgesToDeployOrDelete.addAll(TestUtils.getBridgesToDeployOrDelete().as(new TypeRef<List<BridgeDTO>>() {
            }));
            assertThat(bridgesToDeployOrDelete.stream().filter(x -> x.getStatus().equals(PREPARING)).count()).isEqualTo(1);
        });

        BridgeDTO bridge = bridgesToDeployOrDelete.get(0);
        assertThat(bridge.getName()).isEqualTo(DEFAULT_BRIDGE_NAME);
        assertThat(bridge.getCustomerId()).isEqualTo(DEFAULT_CUSTOMER_ID);
        assertThat(bridge.getStatus()).isEqualTo(PREPARING);
        assertThat(bridge.getEndpoint()).isNotNull();
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void testGetBridgesToDelete() {
        TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION));

        final List<BridgeDTO> bridgesToDeployOrDelete = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            bridgesToDeployOrDelete.clear();
            bridgesToDeployOrDelete.addAll(TestUtils.getBridgesToDeployOrDelete().as(new TypeRef<List<BridgeDTO>>() {
            }));
            assertThat(bridgesToDeployOrDelete.size()).isEqualTo(1);
        });

        BridgeDTO bridge = bridgesToDeployOrDelete.get(0);

        bridge.setStatus(READY);
        TestUtils.updateBridge(bridge).then().statusCode(200);

        TestUtils.deleteBridge(bridge.getId()).then().statusCode(202);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            bridgesToDeployOrDelete.clear();
            bridgesToDeployOrDelete.addAll(TestUtils.getBridgesToDeployOrDelete().as(new TypeRef<List<BridgeDTO>>() {
            }));

            assertThat(bridgesToDeployOrDelete.stream().filter(x -> x.getStatus().equals(ManagedResourceStatus.ACCEPTED)).count()).isZero();
            assertThat(bridgesToDeployOrDelete.stream().filter(x -> x.getStatus().equals(DEPROVISION)).count()).isEqualTo(1);
        });
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void testNotifyDeployment() {
        TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION));

        final List<BridgeDTO> bridgesToDeployOrDelete = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            bridgesToDeployOrDelete.clear();
            bridgesToDeployOrDelete.addAll(TestUtils.getBridgesToDeployOrDelete().as(new TypeRef<List<BridgeDTO>>() {
            }));
            assertThat(bridgesToDeployOrDelete.stream().filter(x -> x.getStatus().equals(PREPARING)).count()).isEqualTo(1);
        });

        BridgeDTO bridge = bridgesToDeployOrDelete.get(0);
        bridge.setStatus(PROVISIONING);
        TestUtils.updateBridge(bridge).then().statusCode(200);

        // PROVISIONING Bridges are also notified to the Shard Operator.
        // This ensures Bridges are not dropped should the Shard fail after notifying the Managed a Bridge is being provisioned.
        await().atMost(5, SECONDS).untilAsserted(() -> {
            bridgesToDeployOrDelete.clear();
            bridgesToDeployOrDelete.addAll(TestUtils.getBridgesToDeployOrDelete().as(new TypeRef<List<BridgeDTO>>() {
            }));
            assertThat(bridgesToDeployOrDelete.stream().filter(x -> x.getStatus().equals(PROVISIONING)).count()).isEqualTo(1);
        });
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void testNotifyDeletion() {
        TestUtils.createBridge(new BridgeRequestV1(DEFAULT_BRIDGE_NAME, DEFAULT_CLOUD_PROVIDER, DEFAULT_REGION));

        final List<BridgeDTO> bridgesToDeployOrDelete = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            bridgesToDeployOrDelete.clear();
            bridgesToDeployOrDelete.addAll(TestUtils.getBridgesToDeployOrDelete().as(new TypeRef<List<BridgeDTO>>() {
            }));
            assertThat(bridgesToDeployOrDelete.size()).isEqualTo(1);
        });

        BridgeDTO bridge = bridgesToDeployOrDelete.get(0);

        bridge.setStatus(READY);
        TestUtils.updateBridge(bridge).then().statusCode(200);

        TestUtils.deleteBridge(bridge.getId()).then().statusCode(202);

        BridgeResponse bridgeResponse = TestUtils.getBridge(bridge.getId()).as(BridgeResponse.class);
        assertThat(bridgeResponse.getStatus()).isEqualTo(DEPROVISION);

        bridge.setStatus(DELETED);
        TestUtils.updateBridge(bridge).then().statusCode(200);

        TestUtils.getBridge(bridge.getId()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = DEFAULT_CUSTOMER_ID)
    public void testUnauthorizedRole() {
        reset(jwt);
        when(jwt.getClaim(ACCOUNT_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM)).thenReturn("hacker");
        when(jwt.containsClaim(ACCOUNT_ID_SERVICE_ACCOUNT_ATTRIBUTE_CLAIM)).thenReturn(true);
        TestUtils.getBridgesToDeployOrDelete().then().statusCode(403);
        TestUtils.getProcessorsToDeployOrDelete().then().statusCode(403);
        TestUtils.updateBridge(new BridgeDTO()).then().statusCode(403);
        TestUtils.updateProcessor(new ProcessorDTO()).then().statusCode(403);
    }
}
