package com.pavansingerreddy.note.kafka.producer;

import com.pavansingerreddy.note.kafka.dto.UserAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuditProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-audit:user.audit.v1}")
    private String userAuditTopic;

    /**
     * Publishes a UserAuditEvent to Kafka.
     */
    public void publishAuditEvent(UserAuditEvent event) {
        if (event == null) {
            return;
        }

        String partitionKey = event.getUserId() != null
                ? String.valueOf(event.getUserId())
                : (event.getEmail() != null ? event.getEmail() : "anonymous");

        kafkaTemplate.send(userAuditTopic, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserAuditEvent to Kafka: {}", ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully published UserAuditEvent [action={}, user={}]",
                                event.getAction(), partitionKey);
                    }
                });
    }
}
