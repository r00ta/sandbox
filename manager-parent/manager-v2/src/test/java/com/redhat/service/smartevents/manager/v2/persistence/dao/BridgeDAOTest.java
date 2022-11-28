package com.redhat.service.smartevents.manager.v2.persistence.dao;

import javax.inject.Inject;
import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.redhat.service.smartevents.manager.v2.persistence.models.Bridge;
import com.redhat.service.smartevents.manager.v2.utils.DatabaseManagerUtils;
import com.redhat.service.smartevents.test.resource.PostgresResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import static com.redhat.service.smartevents.manager.v2.utils.Fixtures.createBridge;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
public class BridgeDAOTest {

    @Inject
    BridgeDAO bridgeDAO;

    @Inject
    DatabaseManagerUtils databaseManagerUtils;

    @BeforeEach
    public void cleanUp() {
        databaseManagerUtils.cleanUpAndInitWithDefaultShard();
    }

    @Test
    @Transactional
    public void testStoreBridge() {
        Bridge bridge = createBridge();
        bridgeDAO.persist(bridge);

        Bridge retrieved = bridgeDAO.findById(bridge.getId());
        assertThat(retrieved.getId()).isEqualTo(bridge.getId());
        assertThat(retrieved.getOperation()).isNotNull();
        assertThat(retrieved.getOperation().getType()).isNotNull();
        assertThat(retrieved.getOperation().getRequestedAt()).isNotNull();
    }
}