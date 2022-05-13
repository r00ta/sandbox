package com.redhat.service.smartevents.shard.operator.providers;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.service.smartevents.shard.operator.BridgeIngressServiceImpl;
import com.redhat.service.smartevents.shard.operator.app.Platform;
import com.redhat.service.smartevents.shard.operator.app.PlatformConfigProvider;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class IstioGatewayProviderImpl implements IstioGatewayProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeIngressServiceImpl.class);

    private Service gatewayService;

    private Integer gatewayServiceHttp2Port;

    @ConfigProperty(name = "event-bridge.istio.broker.name")
    Optional<String> name;

    @ConfigProperty(name = "event-bridge.istio.broker.namespace")
    Optional<String> namespace;

    @Inject
    OpenShiftClient openShiftClient;

    @Inject
    PlatformConfigProvider platformConfigProvider;

    void setup(@Observes StartupEvent event) {
        if (name.isEmpty() || namespace.isEmpty()) {
            exit("'event-bridge.istio.broker.name' and 'event-bridge.istio.broker.namespace' config property must be set on k8s platform.");
        }

        if (Platform.OPENSHIFT.equals(platformConfigProvider.getPlatform())) {
            gatewayService = extractOpenshiftGatewayService(openShiftClient);
        } else {
            gatewayService = extractK8sGatewayService(openShiftClient);
        }
        if (gatewayService == null) {
            exit("Could not retrieve the istio gateway service. Please make sure it was properly deployed.");
        }
        Optional<ServicePort> http2Port = gatewayService.getSpec().getPorts().stream().filter(x -> "http2".equals(x.getName())).findFirst();
        if (http2Port.isEmpty()) {
            exit("Could not retrieve the http2 port for the istio gateway service. Please make sure it was properly deployed.");
        }
        gatewayServiceHttp2Port = http2Port.get().getPort();
    }

    private Service extractOpenshiftGatewayService(OpenShiftClient openShiftClient) {
        return openShiftClient.services().inNamespace(namespace.get()).withName(name.get()).get();
    }

    private Service extractK8sGatewayService(KubernetesClient kubernetesClient) {
        return kubernetesClient.services().inNamespace(namespace.get()).withName(name.get()).get();
    }

    @Override
    public Service getIstioGatewayService() {
        return gatewayService;
    }

    @Override
    public Integer getIstioGatewayServicePort() {
        return gatewayServiceHttp2Port;
    }

    private void exit(String message) {
        LOGGER.error(message);
        Quarkus.asyncExit(1);
    }
}