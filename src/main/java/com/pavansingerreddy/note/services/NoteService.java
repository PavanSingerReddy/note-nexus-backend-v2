package com.pavansingerreddy.note.services;

import java.util.List;

import com.pavansingerreddy.note.dto.NoteDto;
import com.pavansingerreddy.note.dto.PagableNoteDto;
import com.pavansingerreddy.note.exception.NoteDoesNotExistsException;
import com.pavansingerreddy.note.exception.UserNotFoundException;
import com.pavansingerreddy.note.model.NoteModel;

public interface NoteService {

    NoteDto createNewNote(NoteModel noteModel, String userEmail) throws UserNotFoundException;

    NoteDto getASpecificNote(String userEmail, Long noteId) throws NoteDoesNotExistsException;

    List<NoteDto> getAllNotes(String userEmail) throws NoteDoesNotExistsException;

    NoteDto updateSpecificNote(String userEmail, Long noteId, NoteModel noteModel) throws NoteDoesNotExistsException;

    NoteDto deleteASpecificNote(String userEmail, Long noteId) throws NoteDoesNotExistsException;

    List<NoteDto> searchNotes(String userEmail, String searchTerm) throws NoteDoesNotExistsException;

    List<NoteDto> searchNotes(String userEmail, String searchTerm, int page, int size)
            throws NoteDoesNotExistsException;

    List<PagableNoteDto> getPagedNotes(String userEmail, int page, int size) throws NoteDoesNotExistsException;
 
    int syncAllNotesToOpenSearch();

}
