package com.pavansingerreddy.note.events.event_listener;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pavansingerreddy.note.entity.Users;
import com.pavansingerreddy.note.events.event_publisher.RegistrationCompleteEvent;
import com.pavansingerreddy.note.exception.UserNotFoundException;
import com.pavansingerreddy.note.services.EmailService;
import com.pavansingerreddy.note.services.UserService;

// declaring it as a component so spring can create beans of this custom filter
@Component
// This is an event which get's triggered after the user successfully creates a
// new user by sending the request to /register endpoint.after creating the new
// user successfully we send the Email to the registered user's email address
// with the verification token so that the user can verify by using his email
// address
public class RegistrationCompleteEventListener implements ApplicationListener<RegistrationCompleteEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationCompleteEventListener.class);

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    // The @Async annotation in Spring is used to indicate that a method should be
    // executed in a separate thread, i.e., asynchronously. When a method is
    // annotated with @Async, Spring will execute it in a separate thread and the
    // caller can continue with other tasks without waiting for the completion of
    // the called method. NOTE : To enable the use of @Async in a Spring
    // application, we need to add @EnableAsync in one of our configuration classes.
    @Async
    // adding transactional annotation so that all the transactions in this event
    // happen in a single hibernate session and not on different hibernate session
    // which may cause error In the context of Hibernate, a transaction is
    // associated with a Session. The @Transactional annotation ensures that all the
    // operations in the method occur within the same Hibernate Session. This can
    // help avoid issues with detached entities, which can occur when an entity is
    // fetched in one Session and then used in another. So, when we annotate the
    // onApplicationEvent method with @Transactional, we are ensuring that the
    // fetching of the User and the saving of the VerificationToken both happen
    // within the same Hibernate Session. This can help avoid the detached entity
    // passed to persist error.
    @Transactional
    @Override
    // here we are Overriding the onApplicationEvent method from our
    // ApplicationListener interface which takes an ApplicationEvent as a parameter
    // here it is taking RegistrationCompleteEvent which is created by us as a
    // parameter
    public void onApplicationEvent(RegistrationCompleteEvent event) {
        // creating the verification token for the user
        try {
            // here we are fetching the user again because the hibernate session is
            // discontinued in this event as we are using async annotation which creates a
            // separate thread so there will be new hibernate session so even though we have
            // the previous user object the hibernate session is discontinued and we get
            // the error as "detached entity passed to persist" this is the reason we are
            // fetching the user details again in this new hibernate session
            Users user = userService.getUserDetailsByEmail(event.getUser().getEmail());
            // we are creating a random uuid string so that it can be used as a verification
            // token
            String token = UUID.randomUUID().toString();

            // saves the verification token in the database with the associated user with an
            // expiration time for the token
            userService.saveVerificationTokenForUser(token, user);

            // event.getApplicationUrl() gives us the application url which contains the
            // root path or url of the frontend application and we are also attaching our
            // verification token to that url and storing a string named url
            String url = event.getApplicationUrl() + "/verifyRegistration?token=" + token;

            LOGGER.warn("Registration verification url is {}", url);

            // send mail to the users

            // This string the message body which is what we see when we open an email we
            // are attaching our frontend url to this message for verifying our user
            String messageBody = "click the link to verify your account : " + url;
            // This is the heading of the email or subject which we see first when we
            // receive an email
            String messageSubject = "Account verification email";
            // we are using our custom emailService class to send the email with the user's
            // email address and the message subject and the message body and also with the
            // mailNoToUse variable as it contains the mail number from our mail providers
            // to use for sending the email for verification
            emailService.sendEmail(user.getEmail(), messageSubject, messageBody, event.getMailNoToUseForSendingEmail());
        } catch (UserNotFoundException e) {
            // if any error occurs we are printing the stackTrace
            e.printStackTrace();
        }
    }

}
