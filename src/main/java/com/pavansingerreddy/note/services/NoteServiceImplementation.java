package com.pavansingerreddy.note.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.pavansingerreddy.note.events.event_publisher.NoteSearchIndexEvent;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pavansingerreddy.note.dto.NoteDto;
import com.pavansingerreddy.note.dto.PagableNoteDto;
import com.pavansingerreddy.note.entity.Note;
import com.pavansingerreddy.note.entity.Users;
import com.pavansingerreddy.note.exception.NoteDoesNotExistsException;
import com.pavansingerreddy.note.exception.UserNotFoundException;
import com.pavansingerreddy.note.model.NoteModel;
import com.pavansingerreddy.note.repository.NoteRepository;
import com.pavansingerreddy.note.repository.UserRepository;
import com.pavansingerreddy.note.search.dto.NoteSearchDocument;
import com.pavansingerreddy.note.search.service.NoteSearchSyncService;
import com.pavansingerreddy.note.utils.DTOConversionUtil;

@Slf4j
@Service
@Transactional
public class NoteServiceImplementation implements NoteService {

    @Autowired
    NoteRepository noteRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    OpenSearchClient openSearchClient;

    @Autowired
    NoteSearchSyncService noteSearchSyncService;

    @Value("${opensearch.search-alias:notes_search}")
    private String searchAlias;

    @Override
    @Caching(evict = {
        @CacheEvict(value = "notesList", key = "#userEmail"),
        @CacheEvict(value = "pagedNotes", allEntries = true)
    })
    public NoteDto createNewNote(NoteModel noteModel, String userEmail) throws UserNotFoundException {
        Note note = new Note();
        BeanUtils.copyProperties(noteModel, note);
        note.setCreatedAt(Date.from(Instant.now()));
        note.setUpdatedAt(Date.from(Instant.now()));

        Optional<Users> user = userRepository.findByEmail(userEmail);
        if (user.isPresent()) {
            note.setUser(user.get());
            note = noteRepository.save(note);

            // Publish domain event: Ingestion executes AFTER transaction commits
            NoteSearchDocument doc = NoteSearchDocument.builder()
                    .noteId(note.getNoteId())
                    .userId(user.get().getUserId())
                    .title(note.getTitle())
                    .content(note.getContent())
                    .createdAt(note.getCreatedAt())
                    .updatedAt(note.getUpdatedAt())
                    .build();
            applicationEventPublisher.publishEvent(
                    new NoteSearchIndexEvent(this, user.get().getUserId(), note.getNoteId(), doc)
            );

            return DTOConversionUtil.noteToNoteDTO(note);
        }

        throw new UserNotFoundException("The user does not exists to create a note for that user");
    }

    @Override
    @Cacheable(value = "singleNote", key = "#userEmail + ':' + #noteId")
    public NoteDto getASpecificNote(String userEmail, Long noteId) throws NoteDoesNotExistsException {
        Optional<Note> optionalNote = noteRepository.findById(noteId);
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);

        if (optionalNote.isPresent() && optionalUser.isPresent()) {
            Note note = optionalNote.get();
            Users user = optionalUser.get();
            if (note.getUser().getUserId() == user.getUserId()) {
                return DTOConversionUtil.noteToNoteDTO(note);
            }
        }

        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    @Cacheable(value = "notesList", key = "#userEmail")
    public List<NoteDto> getAllNotes(String userEmail) throws NoteDoesNotExistsException {
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);
        if (optionalUser.isPresent()) {
            Users user = optionalUser.get();
            List<Note> notes = user.getNotes();
            if (notes != null) {
                List<NoteDto> noteDtos = new ArrayList<>();
                notes.stream()
                        .map(DTOConversionUtil::noteToNoteDTO)
                        .forEach(noteDtos::add);
                return noteDtos;
            }
        }
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "notesList", key = "#userEmail"),
        @CacheEvict(value = "singleNote", key = "#userEmail + ':' + #noteId"),
        @CacheEvict(value = "pagedNotes", allEntries = true)
    })
    public NoteDto updateSpecificNote(String userEmail, Long noteId, NoteModel noteModel)
            throws NoteDoesNotExistsException {
        Optional<Note> optionalNote = noteRepository.findById(noteId);
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);

        if (optionalNote.isPresent() && optionalUser.isPresent()) {
            Note note = optionalNote.get();
            Users user = optionalUser.get();
            if (note.getUser().getUserId() == user.getUserId()) {
                note = DTOConversionUtil.noteModelToNote(noteModel, note);
                note.setUpdatedAt(Date.from(Instant.now()));
                note = noteRepository.save(note);

                // Publish domain event: Ingestion executes AFTER transaction commits
                NoteSearchDocument doc = NoteSearchDocument.builder()
                        .noteId(note.getNoteId())
                        .userId(user.getUserId())
                        .title(note.getTitle())
                        .content(note.getContent())
                        .createdAt(note.getCreatedAt())
                        .updatedAt(note.getUpdatedAt())
                        .build();
                applicationEventPublisher.publishEvent(
                        new NoteSearchIndexEvent(this, user.getUserId(), note.getNoteId(), doc)
                );

                return DTOConversionUtil.noteToNoteDTO(note);
            }
        }
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "notesList", key = "#userEmail"),
        @CacheEvict(value = "singleNote", key = "#userEmail + ':' + #noteId"),
        @CacheEvict(value = "pagedNotes", allEntries = true)
    })
    public NoteDto deleteASpecificNote(String userEmail, Long noteId) throws NoteDoesNotExistsException {
        Optional<Note> optionalNote = noteRepository.findById(noteId);
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);

        if (optionalNote.isPresent() && optionalUser.isPresent()) {
            Note note = optionalNote.get();
            Users user = optionalUser.get();
            if (note.getUser().getUserId() == user.getUserId()) {
                Long userId = user.getUserId();
                noteRepository.delete(note);

                // Publish domain event: Deletion executes AFTER transaction commits
                applicationEventPublisher.publishEvent(
                        new NoteSearchIndexEvent(this, userId, noteId)
                );

                return DTOConversionUtil.noteToNoteDTO(note);
            }
        }
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    public List<NoteDto> searchNotes(String userEmail, String searchTerm) throws NoteDoesNotExistsException {
        return searchNotes(userEmail, searchTerm, 0, 50);
    }

    @Override
    // Multi-tenant routed OpenSearch query with pagination, highlighting, and resilient SQL fallback
    public List<NoteDto> searchNotes(String userEmail, String searchTerm, int page, int size) throws NoteDoesNotExistsException {
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);
        if (optionalUser.isPresent()) {
            Users user = optionalUser.get();
            Long userId = user.getUserId();

            int safePage = Math.max(0, page);
            int safeSize = Math.min(Math.max(1, size), 100);

            // 1. Primary High-Speed Path: OpenSearch with Routing, Highlighting & Caching
            try {
                SearchRequest searchRequest = SearchRequest.of(s -> s
                        .index(searchAlias)
                        .routing(userId.toString())
                        .from(safePage * safeSize)
                        .size(safeSize)
                        .query(q -> q.bool(b -> b
                                .filter(f -> f.term(t -> t.field("userId").value(FieldValue.of(userId.toString()))))
                                .must(m -> m.multiMatch(mm -> mm
                                        .fields("title^3", "title.autocomplete^1.5", "content^1")
                                        .query(searchTerm)
                                        .fuzziness("AUTO:3,6")
                                ))
                        ))
                        .highlight(h -> h
                                .preTags("<em>")
                                .postTags("</em>")
                                .fields("title", hf -> hf)
                                .fields("content", hf -> hf)
                        )
                );

                SearchResponse<NoteSearchDocument> response = openSearchClient.search(searchRequest, NoteSearchDocument.class);

                List<NoteDto> noteDtos = new ArrayList<>();
                for (Hit<NoteSearchDocument> hit : response.hits().hits()) {
                    NoteSearchDocument doc = hit.source();
                    if (doc != null) {
                        NoteDto noteDto = new NoteDto();
                        noteDto.setNoteId(doc.getNoteId() != null ? doc.getNoteId() : 0L);
                        noteDto.setUserId(doc.getUserId());
                        noteDto.setCreatedAt(doc.getCreatedAt());
                        noteDto.setUpdatedAt(doc.getUpdatedAt());

                        // E-commerce search pattern: Snippet highlighting
                        if (hit.highlight() != null && hit.highlight().containsKey("title")) {
                            List<String> titleHighlights = hit.highlight().get("title");
                            noteDto.setTitle(!titleHighlights.isEmpty() ? titleHighlights.get(0) : doc.getTitle());
                        } else {
                            noteDto.setTitle(doc.getTitle() != null ? doc.getTitle() : "");
                        }

                        if (hit.highlight() != null && hit.highlight().containsKey("content")) {
                            List<String> contentHighlights = hit.highlight().get("content");
                            noteDto.setContent(!contentHighlights.isEmpty() ? String.join(" ... ", contentHighlights) : doc.getContent());
                        } else {
                            noteDto.setContent(doc.getContent() != null ? doc.getContent() : "");
                        }

                        noteDtos.add(noteDto);
                    }
                }
                return noteDtos;

            } catch (Exception e) {
                log.warn("OpenSearch search query failed or unavailable, falling back to database SQL search. Reason: {}", e.getMessage());
            }

            // 2. Resilient Fallback Path: Database SQL Search
            List<Note> notes = noteRepository.search(userId, searchTerm);
            if (notes != null) {
                List<NoteDto> noteDtos = new ArrayList<>();
                notes.stream()
                        .map(DTOConversionUtil::noteToNoteDTO)
                        .forEach(noteDtos::add);
                return noteDtos;
            }
        }

        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    @Cacheable(value = "pagedNotes", key = "#userEmail + ':' + #page + ':' + #size")
    public List<PagableNoteDto> getPagedNotes(String userEmail, int page, int size) throws NoteDoesNotExistsException {
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);
        if (optionalUser.isPresent()) {
            Users user = optionalUser.get();
            Long userId = user.getUserId();
            Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
            Page<Note> pagedNotes = noteRepository.findByUser_UserId(userId, pageable);
            List<Note> notes = pagedNotes.getContent();
            if (notes != null) {
                long totalPages = pagedNotes.getTotalPages();
                List<PagableNoteDto> pagableNoteDtos = new ArrayList<>();
                notes.stream()
                        .map(note -> DTOConversionUtil.noteToPagableNoteDto(note, totalPages))
                        .forEach(pagableNoteDtos::add);
                return pagableNoteDtos;
            }
        }
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    public int syncAllNotesToOpenSearch() {
        List<Note> allNotes = noteRepository.findAll();
        log.info("Starting bulk sync of {} notes to OpenSearch", allNotes.size());
        int totalSynced = noteSearchSyncService.bulkIndexNotes(allNotes);
        log.info("Completed bulk sync of {} notes to OpenSearch", totalSynced);
        return totalSynced;
    }
}

