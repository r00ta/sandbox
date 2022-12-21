package com.redhat.service.smartevents.shard.operator.v2.providers;

import com.redhat.service.smartevents.shard.operator.core.providers.IstioGatewayProviderImpl;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.quarkus.test.Mock;

@Mock
public class IstioGatewayProviderMock extends IstioGatewayProviderImpl {

    @Override
    public Service getIstioGatewayService() {
        return new ServiceBuilder()
                .withMetadata(new ObjectMetaBuilder().withName("rhose-ingressgateway").withNamespace("istio-system").build())
                .withSpec(new ServiceSpecBuilder().withPorts(new ServicePortBuilder().withName("http2").withPort(15021).build()).build())
                .build();
    }

    @Override
    public Integer getIstioGatewayServicePort() {
        return 15021;
    }
}
