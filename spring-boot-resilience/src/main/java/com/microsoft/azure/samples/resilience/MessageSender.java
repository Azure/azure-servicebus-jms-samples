package com.microsoft.azure.samples.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Demonstrates sending messages via {@link JmsTemplate} with cached connections.
 *
 * <p>The {@link JmsTemplate} is backed by a
 * {@link org.springframework.jms.connection.CachingConnectionFactory}, which
 * reuses the underlying AMQP connection and sessions across sends. Without
 * caching, each send creates and closes a connection — at volume, this exhausts
 * the broker's AMQP link limit and causes remote link closure errors.</p>
 */
@Component
public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    private final JmsTemplate jmsTemplate;
    private final String queueName;
    private int messageCount;

    public MessageSender(
            JmsTemplate jmsTemplate,
            @Value("${sample.queue-name:testqueue}") String queueName) {
        this.jmsTemplate = jmsTemplate;
        this.queueName = queueName;
    }

    /**
     * Sends a test message every 10 seconds.
     *
     * <p>In a real application, sending would be triggered by business logic
     * (HTTP request, event, scheduled job) rather than a fixed timer.</p>
     */
    @Scheduled(fixedRate = 10_000)
    public void sendMessage() {
        messageCount++;
        String text = "Sample message #" + messageCount;

        try {
            jmsTemplate.convertAndSend(queueName, text);
            log.info("Sent: {}", text);
        } catch (Exception e) {
            log.error("Send failed for message #{}: {}", messageCount, e.getMessage(), e);
        }
    }
}
