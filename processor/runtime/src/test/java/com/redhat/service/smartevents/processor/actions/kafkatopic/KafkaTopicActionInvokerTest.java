package com.redhat.service.smartevents.processor.actions.kafkatopic;

import com.redhat.service.smartevents.infra.models.dto.ProcessorDTO;
import com.redhat.service.smartevents.infra.models.processors.ProcessorType;

class KafkaTopicActionInvokerTest {

    private ProcessorDTO createProcessor() {
        ProcessorDTO p = new ProcessorDTO();
        p.setType(ProcessorType.SINK);
        p.setId("myProcessor");
        p.setBridgeId("myBridge");
        return p;
    }

    //    @Test // TODO: Refactor
    //    void onEvent() {
    //        ArgumentCaptor<Message<String>> captor = ArgumentCaptor.forClass(Message.class);
    //        Emitter<String> emitter = mock(Emitter.class);
    //        String event = "{\"key\": \"value\"}";
    //        String topic = "myTestTopic";
    //        ProcessorDTO processor = createProcessor();
    //
    //        KafkaTopicActionInvoker invoker = new KafkaTopicActionInvoker(emitter, processor, topic);
    //        invoker.onEvent(event, Collections.emptyMap());
    //
    //        verify(emitter).send(captor.capture());
    //
    //        Message<String> sent = captor.getValue();
    //        assertThat(sent.getPayload()).isEqualTo(event);
    //
    //        Metadata metadata = sent.getMetadata();
    //        OutgoingKafkaRecordMetadata recordMetadata = metadata.get(OutgoingKafkaRecordMetadata.class).get();
    //        assertThat(recordMetadata.getTopic()).isEqualTo(topic);
    //    }
}
