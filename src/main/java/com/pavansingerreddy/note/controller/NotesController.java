package com.pavansingerreddy.note.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pavansingerreddy.note.dto.NoteDto;
import com.pavansingerreddy.note.dto.PagableNoteDto;
import com.pavansingerreddy.note.exception.NoteDoesNotExistsException;
import com.pavansingerreddy.note.exception.UserNotFoundException;
import com.pavansingerreddy.note.model.NoteModel;
import com.pavansingerreddy.note.services.NoteService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;

// The @RestController annotation is a convenience annotation in Spring that is used to create RESTful web services. It was introduced in Spring 4.0 to simplify the creation of RESTful web services.

// This annotation is a combination of @Controller and @ResponseBody annotations. The @Controller annotation is used to mark a class as a web request handler, and the @ResponseBody annotation is used to indicate that the return value from a method should be used as the response body for the request.

// By using @RestController, you eliminate the need to annotate every request handling method of the controller class with the @ResponseBody annotation. This means that every method in the controller will automatically serialize return objects into HttpResponse.

@RestController

// RequestMapping annotation is used in spring mvc it can be used both at the
// class level and the method level.When @RequestMapping("/api/notes") is used
// at the class level, it means that all request handling methods in this class
// will be relative to the /api/notes path. For example, if there is a method in
// the class annotated with @RequestMapping("/get"), the full path to access
// this method would be /api/notes/get
@RequestMapping("/api/notes")
// This class is a notes controller class which handles all the request related
// to the notes of the logged in user
public class NotesController {

    // autowiring our NoteService so that the bean or instance of
    // NoteService will be injected here by the spring IOC container
    @Autowired
    NoteService noteService;

    // @PostMapping is a Spring annotation that maps HTTP POST requests onto this
    // method. "/api/notes/create" is the path at which this method will be
    // available.
    @PostMapping("/create")
    // @RolesAllowed is a Java annotation used to specify the security roles
    // permitted to access method(s) in an application.
    // The "USER" role is allowed to access this method.
    @RolesAllowed("USER")
    // This method returns a ResponseEntity containing a NoteDto object.
    // It throws a UserNotFoundException if the user is not found.
    // The Principal object represents the currently authenticated user.
    // @RequestBody is a Spring annotation which is used to get JSON object in the
    // request body which will be converted to a NoteModel object.
    // @Valid is a Java annotation used to validate that the parameters of the
    // method or constructor are valid. it contains the title and content in it
    public ResponseEntity<NoteDto> createNewNote(Principal principal, @RequestBody @Valid NoteModel noteModel)
            throws UserNotFoundException {
        // Get the name (email) of the authenticated user.
        String userEmail = principal.getName();
        // Call a method in noteService to create a new note with the details from the
        // NoteModel object and the user's email. Return the created NoteDto object in
        // the response with a status of 200 OK.
        return ResponseEntity.ok(noteService.createNewNote(noteModel, userEmail));
    }

    // @GetMapping is a Spring annotation that maps HTTP GET requests onto this
    // method."/api/notes/get/{noteId}" is the path at which this method will be
    // available. The {noteId} is a path variable.
    @GetMapping("/get/{noteId}")
    // @RolesAllowed is a Java annotation used to specify the security roles
    // permitted to access method(s) in an application.The "USER" role is allowed to
    // access this method.
    @RolesAllowed("USER")
    // This method returns a ResponseEntity containing a NoteDto object.
    // It throws a NoteDoesNotExistsException if the note is not found.
    // The Principal object represents the currently authenticated user.
    // @PathVariable is a Spring annotation which indicates that a method parameter
    // should be bound to a URI template variable.Here it's binding the path
    // variable {noteId} to the method parameter noteId.
    public ResponseEntity<NoteDto> getASpecificNote(Principal principal, @PathVariable("noteId") Long noteId)
            throws NoteDoesNotExistsException {
        // Get the name (email) of the authenticated user.
        String userEmail = principal.getName();
        // Call a method in noteService to get a specific note identified by the noteId
        // and the user's email.It checks if the note is present with the given id and
        // the user is also present with the given email if they both are present then
        // if checks if the note belongs to the given user or not after checking all
        // that It Return the NoteDto object in the response with a status
        // of 200 OK.
        return ResponseEntity.ok(noteService.getASpecificNote(userEmail, noteId));
    }

    // @GetMapping is a Spring annotation that maps HTTP GET requests onto this
    // method. "/api/notes/get" is the path at which this method will be available.
    @GetMapping("/get")
    // @RolesAllowed is a Java annotation used to specify the security roles
    // permitted to access method(s) in an application.The "USER" role is allowed to
    // access this method.
    @RolesAllowed("USER")
    // This method returns a ResponseEntity containing a list of NoteDto objects.
    // It throws a NoteDoesNotExistsException if no notes are found.
    // The Principal object represents the currently authenticated user.
    public ResponseEntity<List<NoteDto>> getAllNotes(Principal principal) throws NoteDoesNotExistsException {
        // Get the name (email) of the authenticated user.
        String userEmail = principal.getName();
        // Call a method in noteService to get all notes associated with the user's
        // email. Return the list of NoteDto objects in the response with a status of
        // 200 OK.
        return ResponseEntity.ok(noteService.getAllNotes(userEmail));
    }

    // @PutMapping is a Spring annotation that maps HTTP PUT requests onto this
    // method. "/api/notes/edit/{noteId}" is the path at which this method will be
    // available. The {noteId} is a path variable.
    @PutMapping("/edit/{noteId}")
    // @RolesAllowed is a Java annotation used to specify the security roles
    // permitted to access method(s) in an application. The "USER" role is allowed
    // to access this method.
    @RolesAllowed("USER")
    // This method returns a ResponseEntity containing a NoteDto object.
    // It throws a NoteDoesNotExistsException if the note is not found.
    // The Principal object represents the currently authenticated user.
    // @PathVariable is a Spring annotation which indicates that a method parameter
    // should be bound to a URI template variable. Here it's binding the path
    // variable {noteId} to the method parameter noteId. @RequestBody is a Spring
    // annotation which is used to get a JSON object in the request body which will
    // be converted to a NoteModel object.
    public ResponseEntity<NoteDto> updateSpecificNote(Principal principal, @PathVariable("noteId") Long noteId,
            @RequestBody NoteModel noteModel) throws NoteDoesNotExistsException {
        // Get the name (email) of the authenticated user.
        String userEmail = principal.getName();
        // Call a method in noteService to update a specific note identified by the
        // noteId and the user's email with the details from the NoteModel object.
        // Return the updated NoteDto object in the response with a status of 200 OK.
        return ResponseEntity.ok(noteService.updateSpecificNote(userEmail, noteId, noteModel));
    }

    // @DeleteMapping is a Spring annotation that maps HTTP DELETE requests onto
    // this method. "/api/notes/delete/{noteId}" is the path at which this method
    // will be available. The {noteId} is a path variable.
    @DeleteMapping("/delete/{noteId}")
    // @RolesAllowed is a Java annotation used to specify the security roles
    // permitted to access method(s) in an application.
    // The "USER" role is allowed to access this method.
    @RolesAllowed("USER")
    // This method returns a ResponseEntity containing a NoteDto object.
    // It throws a NoteDoesNotExistsException if the note is not found.
    // The Principal object represents the currently authenticated user.
    // @PathVariable is a Spring annotation which indicates that a method parameter
    // should be bound to a URI template variable. Here it's binding the path
    // variable {noteId} to the method parameter noteId.

    public ResponseEntity<NoteDto> deleteASpecificNote(Principal principal, @PathVariable("noteId") Long noteId)
            throws NoteDoesNotExistsException {

        // Get the name (email) of the authenticated user.
        String userEmail = principal.getName();
        // Call a method in noteService to delete a specific note identified by the
        // noteId and the user's email. Return the deleted NoteDto object in the
        // response with a status of 200 OK.

        return ResponseEntity.ok(noteService.deleteASpecificNote(userEmail, noteId));
    }

    // @GetMapping is a Spring annotation that maps HTTP GET requests onto this
    // method. "/api/notes/search" is the path at which this method will be
    // available.
    @GetMapping("/search")
    @RolesAllowed("USER")
    public ResponseEntity<List<NoteDto>> getNotes(
            Principal principal,
            @RequestParam(name = "term") String searchTerm,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size)
            throws NoteDoesNotExistsException {
        String userEmail = principal.getName();
        return ResponseEntity.ok(noteService.searchNotes(userEmail, searchTerm, page, size));
    }

    // @GetMapping is a Spring annotation that maps HTTP GET requests onto this
    // method. "/api/notes/get/paged" is the path at which this method will be
    // available.
    @GetMapping("/get/paged")
    // This method returns a ResponseEntity containing a list of PagableNoteDto
    // objects.PagableNoteDto is similar to noteDto but it contains additional
    // attribute which specifies total pages. The Principal object represents the
    // currently authenticated user. @RequestParam is a Spring annotation which
    // indicates that a method parameter should be bound to a web request query
    // parameter. Here it's binding the request query parameter "page" to the method
    // parameter page. If "page" is not provided in the request, it defaults to 0.
    // Here it's binding the request query parameter "size" to the method parameter
    // size. If "size" is not provided in the request, it defaults to 10.

    public ResponseEntity<List<PagableNoteDto>> getPagedNotes(Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) throws NoteDoesNotExistsException {

        // Get the name (email) of the authenticated user.
        String userEmail = principal.getName();
        // Call a method in noteService to get a page of notes associated with the
        // user's email. The page number and size are specified by the page and size
        // parameters.

        return ResponseEntity.ok(noteService.getPagedNotes(userEmail, page, size));

    }

    // @PostMapping is a Spring annotation that maps HTTP POST requests onto this
    // method. "/api/notes/sync-all" is the path to bulk update/index all notes
    // from the database into the OpenSearch index.
    @PostMapping("/sync-all")
    @RolesAllowed("USER")
    public ResponseEntity<Map<String, Object>> syncAllNotesToOpenSearch() {
        int totalSynced = noteService.syncAllNotesToOpenSearch();
        return ResponseEntity.ok(Map.of(
                "message", "Successfully bulk-synced all notes to OpenSearch",
                "totalSynced", totalSynced
        ));
    }
}
