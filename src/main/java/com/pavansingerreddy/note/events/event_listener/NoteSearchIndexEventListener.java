package com.pavansingerreddy.note.events.event_listener;

import com.pavansingerreddy.note.events.event_publisher.NoteSearchIndexEvent;
import com.pavansingerreddy.note.search.service.NoteSearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteSearchIndexEventListener {

    private final NoteSearchSyncService noteSearchSyncService;

    /**
     * Listens for note indexing events ONLY after the database transaction has successfully committed.
     * This eliminates the dual-write problem and guarantees data consistency between SQL and OpenSearch.
     */
    @Async("searchSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNoteSearchIndexEvent(NoteSearchIndexEvent event) {
        log.debug("Received AFTER_COMMIT NoteSearchIndexEvent for action: {}, userId: {}, noteId: {}",
                event.getActionType(), event.getUserId(), event.getNoteId());

        switch (event.getActionType()) {
            case INDEX -> {
                if (event.getDocument() != null) {
                    noteSearchSyncService.indexDocument(event.getDocument());
                }
            }
            case DELETE -> {
                if (event.getUserId() != null && event.getNoteId() != null) {
                    noteSearchSyncService.deleteDocument(event.getUserId(), event.getNoteId());
                }
            }
        }
    }
}
