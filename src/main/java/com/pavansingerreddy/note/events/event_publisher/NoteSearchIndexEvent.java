package com.pavansingerreddy.note.events.event_publisher;

import com.pavansingerreddy.note.search.dto.NoteSearchDocument;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NoteSearchIndexEvent extends ApplicationEvent {

    public enum ActionType {
        INDEX,
        DELETE
    }

    private final ActionType actionType;
    private final Long userId;
    private final Long noteId;
    private final NoteSearchDocument document;

    /**
     * Constructor for single note indexing.
     */
    public NoteSearchIndexEvent(Object source, Long userId, Long noteId, NoteSearchDocument document) {
        super(source);
        this.actionType = ActionType.INDEX;
        this.userId = userId;
        this.noteId = noteId;
        this.document = document;
    }

    /**
     * Constructor for single note deletion.
     */
    public NoteSearchIndexEvent(Object source, Long userId, Long noteId) {
        super(source);
        this.actionType = ActionType.DELETE;
        this.userId = userId;
        this.noteId = noteId;
        this.document = null;
    }
}
