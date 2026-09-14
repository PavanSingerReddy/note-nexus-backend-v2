package com.pavansingerreddy.note.events.event_listener;

import com.pavansingerreddy.note.events.event_publisher.NoteSearchIndexEvent;
import com.pavansingerreddy.note.kafka.dto.NoteEvent;
import com.pavansingerreddy.note.kafka.producer.NoteEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteSearchIndexEventListener {

    private final NoteEventProducer noteEventProducer;

    /**
     * Listens for note domain events ONLY after the database transaction has successfully committed.
     * Forwards the event to the durable Apache Kafka topic for micro-batch ingestion into OpenSearch.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNoteSearchIndexEvent(NoteSearchIndexEvent event) {
        log.debug("Received AFTER_COMMIT NoteSearchIndexEvent for action: {}, userId: {}, noteId: {}",
                event.getActionType(), event.getUserId(), event.getNoteId());

        NoteEvent.EventType eventType = switch (event.getActionType()) {
            case INDEX -> NoteEvent.EventType.CREATED;
            case DELETE -> NoteEvent.EventType.DELETED;
        };

        NoteEvent.NoteEventBuilder builder = NoteEvent.builder()
                .eventType(eventType)
                .userId(event.getUserId())
                .noteId(event.getNoteId());

        if (event.getDocument() != null) {
            builder.title(event.getDocument().getTitle())
                    .content(event.getDocument().getContent())
                    .createdAt(event.getDocument().getCreatedAt())
                    .updatedAt(event.getDocument().getUpdatedAt());
        }

        noteEventProducer.publishNoteEvent(builder.build());
    }
}
