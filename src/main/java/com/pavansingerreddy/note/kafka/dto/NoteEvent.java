package com.pavansingerreddy.note.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteEvent implements Serializable {

    public enum EventType {
        CREATED,
        UPDATED,
        DELETED
    }

    private EventType eventType;
    private Long noteId;
    private Long userId;
    private String title;
    private String content;
    private Date createdAt;
    private Date updatedAt;
}
