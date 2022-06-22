package com.redhat.service.smartevents.processor.actions.kafkatopic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.service.smartevents.infra.models.dto.ProcessorDTO;
import com.redhat.service.smartevents.processor.actions.ActionInvoker;

import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

public class KafkaTopicActionInvoker implements ActionInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaTopicActionInvoker.class);

    private final String topic;

    private final ProcessorDTO processor;

    private final KafkaProducer<Integer, String> producer;

    public KafkaTopicActionInvoker(KafkaProducer<Integer, String> producer, ProcessorDTO processor, String topic) {
        this.producer = producer;
        this.topic = topic;
        this.processor = processor;
    }

    @Override
    public void onEvent(String event, Map<String, String> headers) {

        // add headers as Kafka headers
        List<KafkaHeader> kafkaHeaders = headers
                .entrySet()
                .stream()
                .map(th -> KafkaHeader.header(th.getKey(), th.getValue().getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.toList());

        KafkaProducerRecord<Integer, String> record = KafkaProducerRecord.create(topic, new Random().nextInt(), event).addHeaders(kafkaHeaders);
        producer.send(record);
        LOG.info("Emitted CloudEvent to target topic '{}' for Action on Processor '{}' on Bridge '{}'", topic, processor.getId(), processor.getBridgeId());
    }
}
