package com.redhat.service.smartevents.processor.resolvers.custom;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.fasterxml.jackson.databind.node.TextNode;
import com.redhat.service.smartevents.infra.models.gateways.Action;
import com.redhat.service.smartevents.processor.GatewayConfiguratorService;
import com.redhat.service.smartevents.processor.GatewayResolver;
import com.redhat.service.smartevents.processor.actions.kafkatopic.KafkaTopicAction;
import com.redhat.service.smartevents.processor.actions.slack.SlackAction;

@ApplicationScoped
public class SlackActionResolver implements SlackAction, GatewayResolver<Action> {

    @Inject
    GatewayConfiguratorService gatewayConfiguratorService;

    @Override
    public Action resolve(Action action, String customerId, String bridgeId, String processorId) {

        Action resolvedAction = new Action();

        resolvedAction.setParameters(action.getParameters().deepCopy());
        resolvedAction.setType(KafkaTopicAction.TYPE);

        resolvedAction.getParameters().set(KafkaTopicAction.TOPIC_PARAM, new TextNode(gatewayConfiguratorService.getConnectorTopicName(processorId)));
        resolvedAction.getParameters().set(KafkaTopicAction.CLIENT_ID, new TextNode(gatewayConfiguratorService.getClientId()));
        resolvedAction.getParameters().set(KafkaTopicAction.CLIENT_SECRET, new TextNode(gatewayConfiguratorService.getClientSecret()));
        resolvedAction.getParameters().set(KafkaTopicAction.BROKER_URL, new TextNode(gatewayConfiguratorService.getBootstrapServers()));

        return resolvedAction;
    }
}
