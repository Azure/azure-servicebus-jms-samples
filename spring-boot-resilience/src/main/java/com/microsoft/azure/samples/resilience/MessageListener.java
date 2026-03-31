package com.microsoft.azure.samples.resilience;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Demonstrates a resilient JMS listener with proper error handling.
 *
 * <p>This listener uses the {@code jmsListenerContainerFactory} bean defined in
 * {@link JmsConfig}, which is backed by the raw
 * {@link com.azure.servicebus.jms.ServiceBusJmsConnectionFactory}. Each listener
 * container gets its own AMQP connection that can fail and recover independently
 * of other listeners.</p>
 *
 * <p>Error handling follows two categories:</p>
 * <ol>
 *   <li><b>Service/broker error</b> — JMS exception during acknowledgment or
 *       interaction with the broker. Rethrown so the container triggers recovery.</li>
 *   <li><b>Application error</b> — unexpected failure in business logic.
 *       Rethrown so the container can trigger redelivery.</li>
 * </ol>
 */
@Component
public class MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageListener.class);

    /**
     * Receives messages from the configured queue.
     *
     * <p>The {@code containerFactory} attribute explicitly references the listener
     * container factory that uses the raw (un-cached) connection factory. This
     * ensures the listener has its own AMQP connection and can recover from
     * transient failures independently.</p>
     */
    @JmsListener(
            destination = "${sample.queue-name:testqueue}",
            containerFactory = "jmsListenerContainerFactory")
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage textMessage) {
                String text = textMessage.getText();
                log.info("Received: {}", text);
            } else {
                log.info("Received non-text message: {}",
                        message.getClass().getSimpleName());
            }
            message.acknowledge();
        } catch (JMSException e) {
            // Service/broker error during acknowledgment — rethrow so the
            // listener container can trigger its recovery/reconnection logic.
            log.error("Failed to acknowledge message: {}", e.getMessage(), e);
            throw new RuntimeException("JMS broker error during acknowledgment", e);
        } catch (Exception e) {
            // Application processing error — rethrow so the container sees the
            // failure and can trigger redelivery rather than silently succeeding.
            log.error("Error processing message: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing JMS message", e);
        }
    }
}
