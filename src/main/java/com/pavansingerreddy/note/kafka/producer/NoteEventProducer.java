package com.pavansingerreddy.note.kafka.producer;

import com.pavansingerreddy.note.kafka.dto.NoteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.note-events:notes.events.v1}")
    private String noteEventsTopic;

    /**
     * Publishes a NoteEvent partitioned strictly by userId.
     * This guarantees in-order event delivery (CREATED -> UPDATED -> DELETED) for any individual user.
     */
    public void publishNoteEvent(NoteEvent event) {
        if (event == null || event.getUserId() == null) {
            log.warn("Cannot publish NoteEvent with null event or userId");
            return;
        }

        String partitionKey = String.valueOf(event.getUserId());

        kafkaTemplate.send(noteEventsTopic, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish NoteEvent to Kafka for userId={}, noteId={}: {}",
                                event.getUserId(), event.getNoteId(), ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully published NoteEvent [action={}, noteId={}] to partition={}",
                                event.getEventType(), event.getNoteId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
