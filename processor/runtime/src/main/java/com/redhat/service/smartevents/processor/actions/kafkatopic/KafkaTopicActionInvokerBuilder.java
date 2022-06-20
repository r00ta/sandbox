package com.redhat.service.smartevents.processor.actions.kafkatopic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.service.smartevents.infra.models.dto.ProcessorDTO;
import com.redhat.service.smartevents.infra.models.gateways.Action;
import com.redhat.service.smartevents.processor.actions.ActionInvoker;
import com.redhat.service.smartevents.processor.actions.ActionInvokerBuilder;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;

@ApplicationScoped
public class KafkaTopicActionInvokerBuilder implements KafkaTopicAction,
        ActionInvokerBuilder {

    public static final long DEFAULT_LIST_TOPICS_TIMEOUT = 10L;
    public static final TimeUnit DEFAULT_LIST_TOPICS_TIMEUNIT = TimeUnit.SECONDS;

    @Inject
    Vertx vertx;

    @Override
    public ActionInvoker build(ProcessorDTO processor, Action action) {
        String requiredTopic = action.getParameter(TOPIC_PARAM);
        String brokerURL = action.getParameter(KAFKA_BROKER_URL);
        String clientId = action.getParameter(KAFKA_CLIENT_ID);
        String clientSecret = action.getParameter(KAFKA_CLIENT_SECRET);

        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", brokerURL);
        config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("acks", "1");
        config.put("sasl.mechanism", "PLAIN");
        config.put("security.protocol", "SASL_SSL");
        config.put("sasl.jaas.config", String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";", clientId, clientSecret));

        // use producer for interacting with Apache Kafka
        KafkaProducer<String, String> producer = KafkaProducer.create(vertx, config);

        return new KafkaTopicActionInvoker(producer, processor, requiredTopic);
    }
}
