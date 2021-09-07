package com.redhat.developer.manager.dao;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;

import com.redhat.developer.infra.dto.BridgeStatus;
import com.redhat.developer.manager.models.Bridge;
import com.redhat.developer.manager.models.ListResult;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;

@ApplicationScoped
@Transactional
public class BridgeDAO implements PanacheRepositoryBase<Bridge, String> {

    public List<Bridge> findByStatus(BridgeStatus status) {
        Parameters params = Parameters
                .with("status", status);
        return find("#BRIDGE.findByStatus", params).list();
    }

    public Bridge findByNameAndCustomerId(String name, String customerId) {
        Parameters params = Parameters
                .with("name", name).and("customerId", customerId);
        return find("#BRIDGE.findByNameAndCustomerId", params).firstResult();
    }

    public Bridge findByIdAndCustomerId(String id, String customerId) {
        Parameters params = Parameters
                .with("id", id).and("customerId", customerId);
        return find("#BRIDGE.findByIdAndCustomerId", params).firstResult();
    }

    public ListResult<Bridge> listByCustomerId(String customerId, int page, int pageSize) {
        Parameters parameters = Parameters.with("customerId", customerId);
        long total = find("#BRIDGE.findByCustomerId", parameters).count();
        List<Bridge> bridges = find("#BRIDGE.findByCustomerId", parameters).page(page, pageSize).list();
        return new ListResult<>(bridges, page, total);
    }
}
