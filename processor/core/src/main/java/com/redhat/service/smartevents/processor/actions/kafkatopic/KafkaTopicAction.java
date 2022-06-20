package com.redhat.service.smartevents.processor.actions.kafkatopic;

import com.redhat.service.smartevents.processor.GatewayBean;

public interface KafkaTopicAction extends GatewayBean {

    String TYPE = "kafka_topic_sink_0.1";
    String TOPIC_PARAM = "topic";
    String KAFKA_BROKER_URL = "kafka_broker_url";
    String KAFKA_CLIENT_ID = "kafka_client_id";
    String KAFKA_CLIENT_SECRET = "kafka_client_secret";

    @Override
    default String getType() {
        return TYPE;
    }
}
