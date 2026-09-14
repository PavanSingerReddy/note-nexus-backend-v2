package com.pavansingerreddy.note.kafka.producer;

import com.pavansingerreddy.note.kafka.dto.EmailNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.email-notifications:notifications.email.v1}")
    private String emailNotificationsTopic;

    /**
     * Publishes an EmailNotificationEvent keyed by toEmail.
     */
    public void publishEmailNotification(EmailNotificationEvent event) {
        if (event == null || event.getToEmail() == null) {
            log.warn("Cannot publish EmailNotificationEvent with null event or recipient");
            return;
        }

        String partitionKey = event.getToEmail();

        kafkaTemplate.send(emailNotificationsTopic, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish EmailNotificationEvent to Kafka for recipient={}: {}",
                                event.getToEmail(), ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully published EmailNotificationEvent [type={}, to={}] to partition={}",
                                event.getType(), event.getToEmail(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
