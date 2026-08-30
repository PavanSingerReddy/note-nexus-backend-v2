package com.pavansingerreddy.note.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoteSearchDocument {
    private Long noteId;
    private Long userId;
    private String title;
    private String content;
    private Date createdAt;
    private Date updatedAt;
}
