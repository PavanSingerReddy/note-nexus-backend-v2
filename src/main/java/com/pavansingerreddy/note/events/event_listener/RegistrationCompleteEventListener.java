package com.pavansingerreddy.note.events.event_listener;

import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pavansingerreddy.note.entity.Users;
import com.pavansingerreddy.note.events.event_publisher.RegistrationCompleteEvent;
import com.pavansingerreddy.note.kafka.dto.EmailNotificationEvent;
import com.pavansingerreddy.note.kafka.dto.UserAuditEvent;
import com.pavansingerreddy.note.kafka.producer.EmailNotificationProducer;
import com.pavansingerreddy.note.kafka.producer.UserAuditProducer;
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
    private EmailNotificationProducer emailNotificationProducer;

    @Autowired
    private UserAuditProducer userAuditProducer;

    @Transactional
    @Override
    public void onApplicationEvent(RegistrationCompleteEvent event) {
        Users user = event.getUser();
        String token = UUID.randomUUID().toString();

        // saves the verification token in the database with the associated user
        userService.saveVerificationTokenForUser(token, user);

        String url = event.getApplicationUrl() + "/verifyRegistration?token=" + token;
        LOGGER.debug("Registration verification url is {}", url);

        String messageBody = "click the link to verify your account : " + url;
        String messageSubject = "Account verification email";

        // Publish email notification event to Kafka (asynchronous and non-blocking)
        EmailNotificationEvent emailEvent = EmailNotificationEvent.builder()
                .type(EmailNotificationEvent.NotificationType.REGISTRATION_VERIFICATION)
                .toEmail(user.getEmail())
                .subject(messageSubject)
                .body(messageBody)
                .mailNoToUse(event.getMailNoToUseForSendingEmail())
                .build();
        emailNotificationProducer.publishEmailNotification(emailEvent);

        // Publish user audit event to Kafka (asynchronous and non-blocking)
        UserAuditEvent auditEvent = UserAuditEvent.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .action(UserAuditEvent.AuditAction.USER_REGISTERED)
                .timestamp(new Date())
                .details("User registered, verification email queued via Kafka")
                .build();
        userAuditProducer.publishAuditEvent(auditEvent);
    }
}
