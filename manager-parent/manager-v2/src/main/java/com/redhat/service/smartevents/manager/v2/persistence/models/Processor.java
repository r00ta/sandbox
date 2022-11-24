package com.redhat.service.smartevents.manager.v2.persistence.models;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.service.smartevents.manager.core.models.ManagedResource;

import io.quarkiverse.hibernate.types.json.JsonBinaryType;
import io.quarkiverse.hibernate.types.json.JsonTypes;

@Entity(name = "Processor_V2")
@TypeDef(name = JsonTypes.JSON_BIN, typeClass = JsonBinaryType.class)
@Table(name = "PROCESSOR_V2", uniqueConstraints = { @UniqueConstraint(columnNames = { "name", "bridge_id" }) })
public class Processor extends ManagedResource {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bridge_id")
    private Bridge bridge;

    @Column(name = "owner", nullable = false)
    private String owner;

    @Type(type = JsonTypes.JSON_BIN)
    @Column(name = "flows", columnDefinition = JsonTypes.JSON_BIN, nullable = false)
    protected JsonNode flows;

    public Bridge getBridge() {
        return bridge;
    }

    public void setBridge(Bridge bridge) {
        this.bridge = bridge;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public JsonNode getFlows() {
        return flows;
    }

    public void setFlows(JsonNode flows) {
        this.flows = flows;
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
        Processor processor = (Processor) o;
        return id.equals(processor.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Processor{" +
                "flows=" + flows +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", submittedAt=" + submittedAt +
                ", publishedAt=" + publishedAt +
                ", bridge=" + bridge +
                ", owner='" + owner + '\'' +
                '}';
    }
}
