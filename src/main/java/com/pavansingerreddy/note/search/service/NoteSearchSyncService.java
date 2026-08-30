package com.pavansingerreddy.note.search.service;

import com.pavansingerreddy.note.entity.Note;
import com.pavansingerreddy.note.search.dto.NoteSearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteSearchSyncService {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.write-alias:notes_write}")
    private String writeAlias;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 200;

    /**
     * Synchronizes a single document to OpenSearch with exponential backoff retry.
     */
    public void indexDocument(NoteSearchDocument doc) {
        if (doc == null || doc.getUserId() == null || doc.getNoteId() == null) {
            return;
        }

        Long userId = doc.getUserId();
        Long noteId = doc.getNoteId();

        executeWithRetry("indexNote:" + noteId, () -> {
            IndexRequest<NoteSearchDocument> indexRequest = IndexRequest.of(i -> i
                    .index(writeAlias)
                    .id(String.valueOf(noteId))
                    .routing(String.valueOf(userId))
                    .document(doc));
            openSearchClient.index(indexRequest);
            log.debug("Successfully indexed note {} for user {}", noteId, userId);
        }, () -> logDeadLetter("INDEX", userId, noteId, doc));
    }

    /**
     * Deletes a document from OpenSearch with exponential backoff retry.
     */
    public void deleteDocument(Long userId, Long noteId) {
        if (userId == null || noteId == null) {
            return;
        }

        executeWithRetry("deleteNote:" + noteId, () -> {
            DeleteRequest deleteRequest = DeleteRequest.of(d -> d
                    .index(writeAlias)
                    .id(String.valueOf(noteId))
                    .routing(String.valueOf(userId)));
            openSearchClient.delete(deleteRequest);
            log.debug("Successfully deleted note {} for user {}", noteId, userId);
        }, () -> logDeadLetter("DELETE", userId, noteId, null));
    }

    /**
     * Performs bulk indexing using the OpenSearch _bulk API (e-commerce pattern).
     */
    public void bulkIndexDocuments(List<NoteSearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        executeWithRetry("bulkIndexAll", () -> {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (NoteSearchDocument doc : documents) {
                if (doc != null && doc.getNoteId() != null && doc.getUserId() != null) {
                    bulkBuilder.operations(op -> op
                            .index(idx -> idx
                                    .index(writeAlias)
                                    .id(String.valueOf(doc.getNoteId()))
                                    .routing(String.valueOf(doc.getUserId()))
                                    .document(doc)));
                }
            }

            BulkResponse bulkResponse = openSearchClient.bulk(bulkBuilder.build());
            if (bulkResponse.errors()) {
                log.error("Bulk indexing completed with errors");
                for (BulkResponseItem item : bulkResponse.items()) {
                    if (item.error() != null) {
                        log.error("Failed to bulk index note id {}: {}", item.id(), item.error().reason());
                    }
                }
            } else {
                log.info("Successfully bulk-indexed {} notes", documents.size());
            }
        }, () -> log.error("DEAD-LETTER: Bulk indexing failed permanently ({} documents)", documents.size()));
    }

    /**
     * Converts a list of Note entities to search documents and indexes them in batches.
     */
    public int bulkIndexNotes(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return 0;
        }

        List<NoteSearchDocument> documents = notes.stream()
                .filter(note -> note != null && note.getUser() != null)
                .map(note -> NoteSearchDocument.builder()
                        .noteId(note.getNoteId())
                        .userId(note.getUser().getUserId())
                        .title(note.getTitle())
                        .content(note.getContent())
                        .createdAt(note.getCreatedAt())
                        .updatedAt(note.getUpdatedAt())
                        .build())
                .toList();

        int batchSize = 500;
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<NoteSearchDocument> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            bulkIndexDocuments(batch);
        }

        return documents.size();
    }

    /**
     * Executes an operation with exponential backoff retries.
     */
    private void executeWithRetry(String operationName, RunnableWithException operation, Runnable onPermanentFailure) {
        int attempts = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempts < MAX_RETRIES) {
            try {
                operation.run();
                return;
            } catch (Exception e) {
                attempts++;
                log.warn("Transient error during OpenSearch operation '{}' (attempt {}/{}): {}",
                        operationName, attempts, MAX_RETRIES, e.getMessage());
                if (attempts >= MAX_RETRIES) {
                    log.error("Exhausted all {} retries for OpenSearch operation '{}'", MAX_RETRIES, operationName);
                    if (onPermanentFailure != null) {
                        onPermanentFailure.run();
                    }
                    return;
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff *= 2;
            }
        }
    }

    private void logDeadLetter(String action, Long userId, Long noteId, Object payload) {
        log.error(
                "DEAD-LETTER-QUEUE: Failed to sync note to OpenSearch [Action: {}, UserId: {}, NoteId: {}, Payload: {}]",
                action, userId, noteId, payload);
    }

    @FunctionalInterface
    private interface RunnableWithException {
        void run() throws Exception;
    }
}
