package com.pavansingerreddy.note.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationEvent implements Serializable {

    public enum NotificationType {
        REGISTRATION_VERIFICATION,
        PASSWORD_RESET
    }

    private NotificationType type;
    private String toEmail;
    private String subject;
    private String body;
    private int mailNoToUse;
}
