package com.pavansingerreddy.note.kafka.consumer;

import com.pavansingerreddy.note.kafka.dto.UserAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserAuditConsumer {

    /**
     * Consumes user audit events for activity logging and security auditing.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.user-audit:user.audit.v1}",
            groupId = "user-audit-group",
            containerFactory = "singleKafkaListenerContainerFactory"
    )
    public void consumeAuditEvent(UserAuditEvent event) {
        if (event == null) {
            return;
        }

        log.info("AUDIT LOG: [Action: {}] [User: {} / {}] [Time: {}] - Details: {}",
                event.getAction(),
                event.getUserId(),
                event.getEmail(),
                event.getTimestamp(),
                event.getDetails());
    }
}
