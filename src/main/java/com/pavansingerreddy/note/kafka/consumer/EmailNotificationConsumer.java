package com.pavansingerreddy.note.kafka.consumer;

import com.pavansingerreddy.note.kafka.dto.EmailNotificationEvent;
import com.pavansingerreddy.note.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final EmailService emailService;

    /**
     * Consumes email dispatch requests from Kafka and invokes the SMTP service.
     * If sending fails, an exception is thrown to trigger the Kafka exponential backoff retry.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.email-notifications:notifications.email.v1}",
            groupId = "email-notification-group",
            containerFactory = "singleKafkaListenerContainerFactory"
    )
    public void consumeEmailNotification(EmailNotificationEvent event) {
        if (event == null || event.getToEmail() == null) {
            log.warn("Skipping invalid EmailNotificationEvent: {}", event);
            return;
        }

        log.info("Processing email dispatch via Kafka [type={}, to={}]", event.getType(), event.getToEmail());

        boolean isSent = emailService.sendEmail(
                event.getToEmail(),
                event.getSubject(),
                event.getBody(),
                event.getMailNoToUse()
        );

        if (!isSent) {
            log.warn("Email delivery failed for recipient {}. Throwing exception to trigger Kafka retry.",
                    event.getToEmail());
            throw new RuntimeException("SMTP delivery failed for: " + event.getToEmail());
        }

        log.info("Successfully dispatched email to {} via Kafka consumer", event.getToEmail());
    }

    /**
     * DLT Listener for emails that could not be delivered after all retry attempts.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.email-notifications:notifications.email.v1}.DLT",
            groupId = "email-notification-dlt-group",
            containerFactory = "singleKafkaListenerContainerFactory"
    )
    public void handleDlt(EmailNotificationEvent event,
                          @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                          @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.error("CRITICAL DLT: Email delivery permanently failed after all retries [topic={}, offset={}, to={}, subject={}]",
                topic, offset,
                event != null ? event.getToEmail() : "unknown",
                event != null ? event.getSubject() : "unknown");
    }
}
