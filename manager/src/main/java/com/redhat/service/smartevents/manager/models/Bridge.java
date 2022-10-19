package com.redhat.service.smartevents.manager.models;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ParamDef;

import com.redhat.service.smartevents.infra.models.bridges.BridgeDefinition;

@NamedQueries({
        @NamedQuery(name = "BRIDGE.findByShardIdToDeployOrDelete",
                query = "from Bridge where shard_id=:shardId and " +
                        "( " +
                        "  (status='PREPARING' and dependencyStatus='READY') " +
                        "  or " +
                        "  (status='PROVISIONING' and dependencyStatus='READY') " +
                        "  or " +
                        "  (status='DEPROVISION' and dependencyStatus='DELETED') " +
                        "  or " +
                        "  (status='DELETING' and dependencyStatus='DELETED') " +
                        ")"),
        @NamedQuery(name = "BRIDGE.findByNameAndCustomerId",
                query = "from Bridge where name=:name and customer_id=:customerId"),
        @NamedQuery(name = "BRIDGE.findByIdAndCustomerId",
                query = "from Bridge where id=:id and customer_id=:customerId"),
        @NamedQuery(name = "BRIDGE.findByCustomerId",
                query = "from Bridge where customer_id=:customerId order by submitted_at desc"),
        @NamedQuery(name = "BRIDGE.findByOrganisationId",
                query = "from Bridge where organisation_id=:organisationId order by submitted_at desc")
})
@Entity
@FilterDefs({
        @FilterDef(name = "byName", parameters = { @ParamDef(name = "name", type = "string") }),
        @FilterDef(name = "byStatus", parameters = { @ParamDef(name = "status", type = "com.redhat.service.smartevents.manager.dao.EnumTypeManagedResourceStatus") })
})
@Filters({
        @Filter(name = "byName", condition = "name like :name"),
        @Filter(name = "byStatus", condition = "status in (:status)")
})
@Table(name = "BRIDGE", uniqueConstraints = { @UniqueConstraint(columnNames = { "name", "customer_id" }) })
public class Bridge extends ManagedDefinedResource<BridgeDefinition> {

    public static final String CUSTOMER_ID_PARAM = "customerId";

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    @Column(name = "shard_id")
    private String shardId;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private String organisationId;

    @Column(name = "owner", nullable = false, updatable = false)
    private String owner;

    @Column(name = "cloud_provider", nullable = false, updatable = false)
    private String cloudProvider;

    @Column(name = "region", nullable = false, updatable = false)
    private String region;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private String subscriptionId;

    public Bridge() {
    }

    public Bridge(String name) {
        this.name = name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getShardId() {
        return shardId;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public String getOwner() {
        return owner;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /*
     * See: https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
     * In the context of JPA equality, our id is our unique business key as we generate it via UUID.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Bridge bridge = (Bridge) o;
        return id.equals(bridge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
