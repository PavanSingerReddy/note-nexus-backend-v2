package com.pavansingerreddy.note.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.pavansingerreddy.note.utils.DTOConversionUtil;

@Service
@Transactional
public class NoteServiceImplementation implements NoteService {

    // autowiring our NoteRepository so that the bean or instance of
    // NoteRepository will be injected here by the spring IOC container
    @Autowired
    NoteRepository noteRepository;
    // autowiring our UserRepository so that the bean or instance of
    // UserRepository will be injected here by the spring IOC container
    @Autowired
    UserRepository userRepository;

    @Override
    @Caching(evict = {
        @CacheEvict(value = "notesList", key = "#userEmail"),
        @CacheEvict(value = "pagedNotes", allEntries = true),
        @CacheEvict(value = "searchNotes", allEntries = true)
    })
    // This method creates a new note by taking the note model which contains note
    // details and the user email as it's parameters
    public NoteDto createNewNote(NoteModel noteModel, String userEmail) throws UserNotFoundException {

        // creating a new note
        Note note = new Note();
        // copying the properties from noteModel to note
        BeanUtils.copyProperties(noteModel, note);
        // setting current time as the note created at time
        note.setCreatedAt(Date.from(Instant.now()));
        // setting current time as the note updated at time.because this note is created
        // just now so it is also updated just now
        note.setUpdatedAt(Date.from(Instant.now()));
        // getting the optional user based on the userEmail string. we
        // want the optional user because it is easy to check if the
        // user exists or not and also it does not have null values in
        // it
        Optional<Users> user = userRepository.findByEmail(userEmail);
        // checking if the user is present or not if the user is present then we set the
        // user of the note to the user who's email is passed to us as the parameter and
        // then saving the note and after that returning the noteDto by converting note
        // to
        // noteDto
        if (user.isPresent()) {
            note.setUser(user.get());
            noteRepository.save(note);
            return DTOConversionUtil.noteToNoteDTO(note);
        }

        // if the user is not present then we throw UserNotFoundException
        throw new UserNotFoundException("The user does not exists to create a note for that user");

    }

    @Override
    @Cacheable(value = "singleNote", key = "#userEmail + ':' + #noteId")
    // This method is used to get a specific note using the user's email and the
    // associated note Id of the user
    public NoteDto getASpecificNote(String userEmail, Long noteId) throws NoteDoesNotExistsException {
        // getting the optional Note based on the noteId. we
        // want the optional Note because it is easy to check if the
        // Note exists or not and also it does not have null values in
        // it
        Optional<Note> optionalNote = noteRepository.findById(noteId);
        // getting the optional User based on the userEmail string. we
        // want the optional User because it is easy to check if the
        // User exists or not and also it does not have null values in
        // it
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);

        // if the note is present and the user is also present then it get's the note
        // and user from the optional note and optional user and after that it checks if
        // the note's user id is equal to the given user's id
        if (optionalNote.isPresent() && optionalUser.isPresent()) {

            Note note = optionalNote.get();
            Users user = optionalUser.get();
            // checks if the note's user id is equal to the given user's id
            if (note.getUser().getUserId() == user.getUserId()) {
                // if the note is associated with the given user then we return the note Dto by
                // converting note to note Dto
                return DTOConversionUtil.noteToNoteDTO(note);
            }
        }

        // if the note or user does not exists with the given parameters then we throw
        // an exception
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");

    }

    @Override
    @Cacheable(value = "notesList", key = "#userEmail")
    // This method retrieves all notes associated with a user identified by their
    // email. It takes the user's email as a parameter. It returns a list of NoteDto
    // objects representing the notes. If no notes are found, it throws a
    // NoteDoesNotExistsException.
    public List<NoteDto> getAllNotes(String userEmail) throws NoteDoesNotExistsException {

        // Call a method in userRepository to find the User object associated with the
        // email. The method returns an Optional, which can either contain the User
        // object (if found) or be empty (if not found).
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);
        // Check if the Optional contains a User object.
        if (optionalUser.isPresent()) {
            // Get the User object from the Optional.
            Users user = optionalUser.get();
            // Get the list of Note objects associated with the user.
            List<Note> notes = user.getNotes();
            // Check if the list of notes is not null.
            if (notes != null) {
                // Create a new list to hold the NoteDto objects.
                List<NoteDto> noteDtos = new ArrayList<>();
                // Convert each Note object to a NoteDto object using a utility method in custom
                // DTOConversionUtil class.Add each NoteDto object to the list.
                notes.stream()
                        .map(DTOConversionUtil::noteToNoteDTO)
                        .forEach(noteDtos::add);
                // Return the list of NoteDto objects.
                return noteDtos;
            }
        }
        // If the Optional does not contain a User object (i.e., the user was not
        // found), or if the user has no notes, throw a NoteDoesNotExistsException.
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "notesList", key = "#userEmail"),
        @CacheEvict(value = "singleNote", key = "#userEmail + ':' + #noteId"),
        @CacheEvict(value = "pagedNotes", allEntries = true),
        @CacheEvict(value = "searchNotes", allEntries = true)
    })
    // This method checks if the user corresponding to the email and the note
    // corresponding to the noteId is present or not if they are present then it
    // checks if the given note's user id is the same as the given user's user id if
    // they both match then the note will be updated with the given note model
    public NoteDto updateSpecificNote(String userEmail, Long noteId, NoteModel noteModel)
            throws NoteDoesNotExistsException {
        // getting the optional Note based on the noteId. we
        // want the optional Note because it is easy to check if the
        // Note exists or not and also it does not have null values in
        // it
        Optional<Note> optionalNote = noteRepository.findById(noteId);
        // getting the optional User based on the userEmail string. we
        // want the optional User because it is easy to check if the
        // User exists or not and also it does not have null values in
        // it
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);

        // if the note is present and the user is also present then it get's the note
        // and user from the optional note and optional user and after that it checks if
        // the note's user id is equal to the given user's id
        if (optionalNote.isPresent() && optionalUser.isPresent()) {
            Note note = optionalNote.get();
            Users user = optionalUser.get();
            // checks if the note's user id is equal to the given user's id
            if (note.getUser().getUserId() == user.getUserId()) {
                // if the note belongs to a particular user then we copy the details from note
                // model to the note entity and then we also update the time at which this note
                // has modified and save that to the database and return the noteDto to the user
                note = DTOConversionUtil.noteModelToNote(noteModel, note);
                note.setUpdatedAt(Date.from(Instant.now()));
                noteRepository.save(note);
                return DTOConversionUtil.noteToNoteDTO(note);
            }
        }
        // if the note or user is not present and also if the note does not belongs to
        // the given user then we throw an exception
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "notesList", key = "#userEmail"),
        @CacheEvict(value = "singleNote", key = "#userEmail + ':' + #noteId"),
        @CacheEvict(value = "pagedNotes", allEntries = true),
        @CacheEvict(value = "searchNotes", allEntries = true)
    })
    // This method checks if the user corresponding to the email and the note
    // corresponding to the noteId is present or not if they are present then it
    // checks if the given note's user id is the same as the given user's user id if
    // they both match then the note will be deleted
    public NoteDto deleteASpecificNote(String userEmail, Long noteId) throws NoteDoesNotExistsException {
        // getting the optional Note based on the noteId. we
        // want the optional Note because it is easy to check if the
        // Note exists or not and also it does not have null values in
        // it
        Optional<Note> optionalNote = noteRepository.findById(noteId);
        // getting the optional User based on the userEmail string. we
        // want the optional User because it is easy to check if the
        // User exists or not and also it does not have null values in
        // it
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);
        // if the note is present and the user is also present then it get's the note
        // and user from the optional note and optional user and after that it checks if
        // the note's user id is equal to the given user's id
        if (optionalNote.isPresent() && optionalUser.isPresent()) {
            Note note = optionalNote.get();
            Users user = optionalUser.get();
            // checks if the note's user id is equal to the given user's id
            if (note.getUser().getUserId() == user.getUserId()) {
                // if they match then we delete the associated note and return the deleted note
                // dto
                noteRepository.delete(note);
                return DTOConversionUtil.noteToNoteDTO(note);
            }
        }
        // if the note or user is not present and also if the note does not belongs to
        // the given user then we throw an exception
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");

    }

    @Override
    @Cacheable(value = "searchNotes", key = "#userEmail + ':' + #searchTerm")
    // This method searches for the given search term in the user's object which is
    // corresponding to the given userEmail.It searches for the search term in the
    // user's note's title and also it's content and returns the corresponding notes
    // which matches the search term
    public List<NoteDto> searchNotes(String userEmail, String searchTerm) throws NoteDoesNotExistsException {
        // getting the optional User based on the userEmail string. we
        // want the optional User because it is easy to check if the
        // User exists or not and also it does not have null values in
        // it
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);
        // checks if the optional user is present or not
                if (optionalUser.isPresent()) {
            // if the optional user is present then we get the user object from the optional
            // user
            Users user = optionalUser.get();
            // we also get the user's id from the user's object
            Long userId = user.getUserId();
            // we search for the corresponding notes related to the given user id and the
            // given search term.we search in the note's title and the note's content with
            // the given search term if any note found which matches the search term in
            // note's title or note's content related to the given user we return the notes
            List<Note> notes = noteRepository.search(userId, searchTerm);
            // checking if the notes which match our search term is not null
            if (notes != null) {

                // creating a list of NoteDto
                List<NoteDto> noteDtos = new ArrayList<>();
                // iterating the notes which matches our search term and then converting the
                // note to noteDto object in the map method and after that we are adding that
                // noteDto's to the list of noteDtos object in the forEach method
                notes.stream()
                        .map(DTOConversionUtil::noteToNoteDTO)
                        .forEach(noteDtos::add);

                // finally we are returning the noteDto's list
                return noteDtos;
            }
        }

        // if the user is not present for the given email or if there exists no notes
        // for the given search term for the particular user then we throw an exception
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");
    }

    @Override
    @Cacheable(value = "pagedNotes", key = "#userEmail + ':' + #page + ':' + #size")
    // This method retrieves a page of notes associated with a user identified by
    // their email. It takes the user's email, the page number, and the page size as
    // parameters. It returns a list of PagableNoteDto objects representing the
    // notes. If no notes are found, it throws a NoteDoesNotExistsException.
    public List<PagableNoteDto> getPagedNotes(String userEmail, int page, int size) throws NoteDoesNotExistsException {
        // Call a method in userRepository to find the User object associated with the
        // email.The method returns an Optional, which can either contain the User
        // object (if found) or be empty (if not found).
        Optional<Users> optionalUser = userRepository.findByEmail(userEmail);
        // Check if the Optional contains a User object.
        if (optionalUser.isPresent()) {
            // Get the User object from the Optional.
            Users user = optionalUser.get();
            // Get the user's ID.
            Long userId = user.getUserId();
            // Create a Pageable object with the specified page number, size, and sort
            // order.The notes will be sorted by the "updatedAt" field in descending
            // order.so the latest updated notes comes first
            Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
            // Call a method in noteRepository to get a page of Note objects associated with
            // the user's ID. The method returns a Page, which is a sublist of items in a
            // list for a specific page number.
            Page<Note> pagedNotes = noteRepository.findByUser_UserId(userId, pageable);
            // Get the content of the Page as a list of Note objects.
            List<Note> notes = pagedNotes.getContent();
            // Check if the list of notes is not null.
            if (notes != null) {
                // Get the total number of pages.
                long totalPages = pagedNotes.getTotalPages();
                // Create a new list to hold the PagableNoteDto objects.
                List<PagableNoteDto> pagableNoteDtos = new ArrayList<>();

                // Convert each Note object to a PagableNoteDto object using a utility method in
                // DTOConversionUtil.Add each PagableNoteDto object to the list.
                notes.stream()
                        .map(note -> DTOConversionUtil.noteToPagableNoteDto(note, totalPages))
                        .forEach(pagableNoteDtos::add);
                return pagableNoteDtos;
            }
        }
        // if the user is not present for the given email or if there exists no notes
        // for the given page and size for the particular user then we throw an
        // exception
        throw new NoteDoesNotExistsException("Note does not exists for the user and NoteId you have provided");

    }

}
