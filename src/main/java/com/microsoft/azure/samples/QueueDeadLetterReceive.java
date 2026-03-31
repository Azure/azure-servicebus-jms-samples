package com.microsoft.azure.samples;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.qpid.jms.JmsQueue;

import com.microsoft.azure.samples.util.ConnectionHelper;
import com.microsoft.azure.samples.util.Constants;

/**
 * Demonstrates how to consume messages from a dead letter queue (DLQ) using JMS.
 *
 * Messages are sent with a short time-to-live (TTL) so they expire into the DLQ.
 * The sample then reads those messages from the DLQ sub-queue and prints their
 * dead-letter metadata.
 *
 * This approach is used because the standard JMS API does not provide a way to
 * explicitly dead-letter (reject) a message. At the AMQP level, the REJECTED
 * disposition maps to DeadLetter(), but JMS only exposes acknowledge and recover.
 * Sending messages with a short TTL is a reliable, self-contained way to populate
 * the DLQ for demonstration purposes.
 */
public class QueueDeadLetterReceive {
    private static final int MESSAGE_COUNT = 3;
    private static final int DELIVERY_MODE = DeliveryMode.PERSISTENT;

    // Short TTL (in milliseconds) so messages expire into the DLQ.
    private static final long SHORT_TTL_MS = 2000;

    // Time to wait (in milliseconds) for messages to expire before reading the DLQ.
    private static final long EXPIRY_WAIT_MS = 5000;

    public static void main(String[] args) throws Exception {
        try {
            /*
             * Initialize the JMS Connection and Session.
             */
            ConnectionFactory factory = ConnectionHelper.createConnectionFactory();
            Connection connection = factory.createConnection();

            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            /*
             * Step 1: Send messages with a short TTL.
             *
             * These messages expire before any consumer picks them up. When the
             * TTL elapses, the broker moves them to the dead letter queue.
             */
            JmsQueue queue = new JmsQueue(Constants.QUEUE);
            MessageProducer producer = session.createProducer(queue);

            System.out.println("Sending " + MESSAGE_COUNT + " messages with " + SHORT_TTL_MS + "ms TTL...");
            long start = System.currentTimeMillis();
            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                TextMessage message = session.createTextMessage("DLQ sample message " + i);
                producer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, SHORT_TTL_MS);
                System.out.println("  Sent message " + i);
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Sent " + MESSAGE_COUNT + " messages in " + elapsed + "ms");
            producer.close();

            /*
             * Step 2: Wait for messages to expire.
             *
             * After the TTL elapses, expired messages are moved to the dead letter
             * queue automatically. No manual intervention is required.
             */
            System.out.println("\nWaiting " + EXPIRY_WAIT_MS + "ms for messages to expire...");
            Thread.sleep(EXPIRY_WAIT_MS);

            /*
             * Step 3: Consume from the queue's dead letter queue.
             *
             * The DLQ is a system sub-queue that exists automatically for every
             * queue and subscription. Access it as a separate JMS destination using
             * the path format: <queue-name>/$deadletterqueue
             *
             * Create a JmsQueue with this full path — no special API is needed.
             */
            String queueDlqPath = Constants.QUEUE + "/$deadletterqueue";
            System.out.println("\nReceiving from queue DLQ: " + queueDlqPath);

            JmsQueue dlqDestination = new JmsQueue(queueDlqPath);
            MessageConsumer dlqConsumer = session.createConsumer(dlqDestination);

            start = System.currentTimeMillis();
            int received = 0;
            int timeout = 2000;
            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                Message message = dlqConsumer.receive(timeout);
                if (message == null) {
                    System.out.println("  No more messages in DLQ (timed out after " + timeout + "ms).");
                    break;
                }
                received++;

                String body = (message instanceof TextMessage)
                        ? ((TextMessage) message).getText()
                        : message.toString();

                /*
                 * Step 4: Read dead-letter metadata.
                 *
                 * Azure Service Bus sets these properties on dead-lettered messages:
                 *   DeadLetterReason         — reason the message was dead-lettered
                 *   DeadLetterErrorDescription — additional detail from the broker
                 *
                 * For TTL-expired messages, these are typically:
                 *   Reason:      "TTLExpiredException"
                 *   Description: "The message expired and was dead lettered."
                 */
                String reason = message.getStringProperty("DeadLetterReason");
                String description = message.getStringProperty("DeadLetterErrorDescription");

                System.out.println("  Message " + i + ":");
                System.out.println("    Body:                       " + body);
                System.out.println("    DeadLetterReason:           " + (reason != null ? reason : "(not set)"));
                System.out.println("    DeadLetterErrorDescription: " + (description != null ? description : "(not set)"));

                message.acknowledge();
            }
            elapsed = System.currentTimeMillis() - start;
            System.out.println("Received " + received + " messages from DLQ in " + elapsed + "ms");

            dlqConsumer.close();

            /*
             * DLQ path reference for topic subscriptions.
             *
             * For topic subscriptions, the DLQ path format is:
             *   <topic>/Subscriptions/<subscription>/$deadletterqueue
             *
             * For example, a subscription named "my-subscription" on a topic
             * named "testtopic" has a DLQ at:
             *   testtopic/Subscriptions/my-subscription/$deadletterqueue
             *
             * Usage is the same — create a JmsQueue with the full path:
             *   String topicDlqPath = Constants.TOPIC
             *       + "/Subscriptions/my-subscription/$deadletterqueue";
             *   JmsQueue topicDlq = new JmsQueue(topicDlqPath);
             *   MessageConsumer consumer = session.createConsumer(topicDlq);
             */
            String topicDlqPath = Constants.TOPIC
                    + "/Subscriptions/my-subscription/$deadletterqueue";
            System.out.println("\nTopic subscription DLQ path (for reference): " + topicDlqPath);

            connection.close();
            System.out.println("\nDone.");
        } catch (Exception exp) {
            System.out.println("Caught exception, exiting.");
            exp.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static class MyExceptionListener implements ExceptionListener {
        public void onException(JMSException exception) {
            System.out.println("Connection ExceptionListener fired, exiting.");
            exception.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
