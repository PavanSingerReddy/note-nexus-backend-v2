package com.pavansingerreddy.note.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.note-events:notes.events.v1}")
    private String noteEventsTopic;

    @Value("${app.kafka.topics.email-notifications:notifications.email.v1}")
    private String emailNotificationsTopic;

    @Value("${app.kafka.topics.user-audit:user.audit.v1}")
    private String userAuditTopic;

    /**
     * Automatic topic creation and partition management.
     */
    @Bean
    public NewTopic noteEventsTopic() {
        return TopicBuilder.name(noteEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic noteEventsDltTopic() {
        return TopicBuilder.name(noteEventsTopic + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailNotificationsTopic() {
        return TopicBuilder.name(emailNotificationsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailNotificationsDltTopic() {
        return TopicBuilder.name(emailNotificationsTopic + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userAuditTopic() {
        return TopicBuilder.name(userAuditTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Centralized resilient error handler with exponential backoff and automatic
     * Dead Letter Topic (DLT) routing when all retries are exhausted.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("Routing failed message from topic {} partition {} offset {} to DLT due to: {}",
                            record.topic(), record.partition(), record.offset(), exception.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", -1);
                });

        // 3 retry attempts with exponential backoff (1s, 2s, 4s)
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    /**
     * Batch container factory specifically designed for high-throughput consumers
     * like OpenSearch bulk ingestion.
     */
    @Bean(name = "batchKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> batchKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(3);
        return factory;
    }

    /**
     * Single-message container factory for sequential operations like email dispatch.
     */
    @Bean(name = "singleKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> singleKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(false);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(3);
        return factory;
    }
}
