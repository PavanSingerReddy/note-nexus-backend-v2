package com.pavansingerreddy.note.kafka.consumer;

import com.pavansingerreddy.note.kafka.dto.NoteEvent;
import com.pavansingerreddy.note.search.dto.NoteSearchDocument;
import com.pavansingerreddy.note.search.service.NoteSearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteSearchIndexConsumer {

    private final NoteSearchSyncService noteSearchSyncService;

    /**
     * Consumes batches of NoteEvents from Kafka and indexes them in OpenSearch
     * using the high-performance _bulk API.
     * Micro-batching drastically reduces network roundtrips to the search cluster.
     */
    @KafkaListener(topics = "${app.kafka.topics.note-events:notes.events.v1}", groupId = "notes-search-indexer-group", containerFactory = "batchKafkaListenerContainerFactory")
    public void consumeNoteEventsBatch(List<NoteEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        log.info("Received batch of {} NoteEvents from Kafka for OpenSearch indexing", events.size());

        List<NoteSearchDocument> documentsToIndex = new ArrayList<>();

        for (NoteEvent event : events) {
            if (event == null || event.getEventType() == null) {
                continue;
            }

            switch (event.getEventType()) {
                case CREATED, UPDATED -> {
                    NoteSearchDocument doc = NoteSearchDocument.builder()
                            .noteId(event.getNoteId())
                            .userId(event.getUserId())
                            .title(event.getTitle())
                            .content(event.getContent())
                            .createdAt(event.getCreatedAt())
                            .updatedAt(event.getUpdatedAt())
                            .build();
                    documentsToIndex.add(doc);
                }
                case DELETED -> {
                    if (event.getUserId() != null && event.getNoteId() != null) {
                        noteSearchSyncService.deleteDocument(event.getUserId(), event.getNoteId());
                    }
                }
            }
        }

        if (!documentsToIndex.isEmpty()) {
            noteSearchSyncService.bulkIndexDocuments(documentsToIndex);
            log.debug("Flushed {} documents to OpenSearch via _bulk API", documentsToIndex.size());
        }
    }

    /**
     * DLT Listener for inspecting poison pills or unrecoverable note events.
     */
    @KafkaListener(topics = "${app.kafka.topics.note-events:notes.events.v1}.DLT", groupId = "notes-search-indexer-dlt-group", containerFactory = "singleKafkaListenerContainerFactory")
    public void handleDlt(NoteEvent event,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.error(
                "DEAD LETTER QUEUE: NoteEvent routed to DLT [topic={}, offset={}, noteId={}, userId={}, eventType={}]",
                topic, offset,
                event != null ? event.getNoteId() : null,
                event != null ? event.getUserId() : null,
                event != null ? event.getEventType() : null);
    }
}
