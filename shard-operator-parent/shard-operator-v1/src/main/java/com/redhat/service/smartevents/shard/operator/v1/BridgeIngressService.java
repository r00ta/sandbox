package com.redhat.service.smartevents.shard.operator.v1;

import com.redhat.service.smartevents.infra.v1.api.models.dto.BridgeDTO;
import com.redhat.service.smartevents.shard.operator.core.resources.knative.KnativeBroker;
import com.redhat.service.smartevents.shard.operator.v1.resources.BridgeIngress;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.istio.security.v1beta1.AuthorizationPolicy;

public interface BridgeIngressService {
    void createBridgeIngress(BridgeDTO bridgeDTO);

    void deleteBridgeIngress(BridgeDTO bridgeDTO);

    void createOrUpdateBridgeIngressSecret(BridgeIngress bridgeIngress, BridgeDTO bridgeDTO);

    Secret fetchBridgeIngressSecret(BridgeIngress bridgeIngress);

    ConfigMap fetchOrCreateBridgeIngressConfigMap(BridgeIngress bridgeIngress, Secret secret);

    KnativeBroker fetchOrCreateBridgeIngressBroker(BridgeIngress bridgeIngress, ConfigMap configMap);

    AuthorizationPolicy fetchOrCreateBridgeIngressAuthorizationPolicy(BridgeIngress bridgeIngress, String path);
}
