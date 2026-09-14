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
public class UserAuditEvent implements Serializable {

    public enum AuditAction {
        USER_REGISTERED,
        USER_VERIFIED,
        PASSWORD_RESET_REQUESTED,
        PASSWORD_RESET_COMPLETED,
        PASSWORD_CHANGED
    }

    private Long userId;
    private String email;
    private AuditAction action;
    private Date timestamp;
    private String details;
}
