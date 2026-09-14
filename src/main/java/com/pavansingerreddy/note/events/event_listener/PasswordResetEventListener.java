package com.pavansingerreddy.note.events.event_listener;

import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pavansingerreddy.note.entity.Users;
import com.pavansingerreddy.note.events.event_publisher.PasswordResetEvent;
import com.pavansingerreddy.note.kafka.dto.EmailNotificationEvent;
import com.pavansingerreddy.note.kafka.dto.UserAuditEvent;
import com.pavansingerreddy.note.kafka.producer.EmailNotificationProducer;
import com.pavansingerreddy.note.kafka.producer.UserAuditProducer;
import com.pavansingerreddy.note.services.UserService;

// making it as component so that spring can recognize it as the component and create a bean for it
@Component
// This event get's triggered when we forget a password and want to reset the
// password and call the api for resetting the password
public class PasswordResetEventListener implements ApplicationListener<PasswordResetEvent> {

    // getting user service so that we can perform the operations related to the
    // user
    @Autowired
    private UserService userService;

    @Autowired
    private EmailNotificationProducer emailNotificationProducer;

    @Autowired
    private UserAuditProducer userAuditProducer;

    @Transactional
    @Override
    public void onApplicationEvent(PasswordResetEvent event) {
        Users user = event.getUser();
        String token = UUID.randomUUID().toString();
        userService.savePasswordResetToken(token, user);

        String url = event.getApplicationUrl() + "/verifyResetPassword?token=" + token;
        String messageBody = "click The link to reset your account password : " + url;
        String messageSubject = "Password Reset Email";

        // Dispatch via Kafka
        EmailNotificationEvent emailEvent = EmailNotificationEvent.builder()
                        .type(EmailNotificationEvent.NotificationType.PASSWORD_RESET)
                        .toEmail(user.getEmail())
                        .subject(messageSubject)
                        .body(messageBody)
                        .mailNoToUse(event.getMailNoToUseForSendingEmail())
                        .build();
        emailNotificationProducer.publishEmailNotification(emailEvent);

        // Audit log via Kafka
        UserAuditEvent auditEvent = UserAuditEvent.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .action(UserAuditEvent.AuditAction.PASSWORD_RESET_REQUESTED)
                        .timestamp(new Date())
                        .details("Password reset token generated and email queued via Kafka")
                        .build();
        userAuditProducer.publishAuditEvent(auditEvent);
    }
}
