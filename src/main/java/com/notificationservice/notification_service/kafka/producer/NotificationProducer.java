package com.notificationservice.notification_service.kafka.producer;

import com.notificationservice.notification_service.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes notification events to Kafka topics.
 * Partition key: channel + hash(recipient) for per-recipient ordering.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private static final String MAIN_TOPIC = "notification.requests";
    private static final String RETRY_TOPIC = "notification.retry";
    private static final String DLQ_TOPIC = "notification.dlq";

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void sendToMainTopic(NotificationEvent event) {
        send(MAIN_TOPIC, event);
    }

    public void sendToRetryTopic(NotificationEvent event) {
        send(RETRY_TOPIC, event);
    }

    public void sendToDlq(NotificationEvent event) {
        send(DLQ_TOPIC, event);
    }

    private void send(String topic, NotificationEvent event) {
        String key = buildPartitionKey(event);

        CompletableFuture<SendResult<String, NotificationEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to {}: notificationId={}, key={}",
                        topic, event.getNotificationId(), key, ex);
            } else {
                log.debug("Published to {} [partition={}, offset={}]: notificationId={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getNotificationId());
            }
        });
    }

    // Same recipient + channel always goes to the same partition for ordering
    private String buildPartitionKey(NotificationEvent event) {
        int recipientHash = Math.abs(event.getRecipient().hashCode());
        return event.getChannel().name() + "-" + recipientHash;
    }
}
