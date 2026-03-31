package com.microsoft.azure.samples;

import java.time.Instant;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
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
 * Demonstrates sending scheduled messages using the JMS 2.0 delivery delay API
 * with Azure Service Bus. One message is sent immediately and another with a
 * 30-second delivery delay. The consumer receives both and prints timestamps
 * showing that the scheduled message arrives after the delay period.
 *
 * <p><strong>Requires Azure Service Bus Premium tier.</strong></p>
 */
public class QueueScheduledSend {
    private static final int DELIVERY_MODE = DeliveryMode.PERSISTENT;
    private static final long DELIVERY_DELAY_MS = 30_000; // 30 seconds
    private static final int RECEIVE_TIMEOUT_MS = 60_000; // 60 seconds

    public static void main(String[] args) throws Exception {
        try {
            /*
             * Initialize the JMS Connection and Session.
             */
            ConnectionFactory factory = ConnectionHelper.createConnectionFactory();
            Connection connection = factory.createConnection();

            Destination queue = new JmsQueue(Constants.QUEUE);

            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            /*
             * Send an immediate message (no delivery delay).
             */
            MessageProducer producer = session.createProducer(queue);

            TextMessage immediateMessage = session.createTextMessage("Immediate message");
            producer.send(immediateMessage, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            Instant sendTime = Instant.now();
            System.out.println("[" + sendTime + "] Sent immediate message.");

            /*
             * Send a scheduled message with a 30-second delivery delay.
             * JMS 2.0 setDeliveryDelay() maps to the Service Bus scheduled
             * enqueue time. The message becomes visible to consumers only
             * after the delay period elapses.
             */
            producer.setDeliveryDelay(DELIVERY_DELAY_MS);

            TextMessage scheduledMessage = session.createTextMessage("Scheduled message (30s delay)");
            producer.send(scheduledMessage, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            Instant scheduledSendTime = Instant.now();
            Instant expectedDelivery = scheduledSendTime.plusMillis(DELIVERY_DELAY_MS);
            System.out.println("[" + scheduledSendTime + "] Sent scheduled message.");
            System.out.println("  Delivery delay: " + DELIVERY_DELAY_MS + " ms");
            System.out.println("  Expected delivery after: " + expectedDelivery);
            System.out.println();
            System.out.println("Waiting for messages (the scheduled message will arrive after ~30s)...");

            producer.close();

            /*
             * Receive both messages. The immediate message should arrive first.
             * The scheduled message should arrive after the delivery delay.
             *
             * getJMSDeliveryTime() returns the earliest time the message
             * becomes deliverable — for the scheduled message this will be
             * approximately scheduledSendTime + DELIVERY_DELAY_MS.
             */
            MessageConsumer consumer = session.createConsumer(queue);

            for (int i = 1; i <= 2; i++) {
                Message received = consumer.receive(RECEIVE_TIMEOUT_MS);
                if (received == null) {
                    System.out.println("[" + Instant.now() + "] No message received within timeout.");
                    break;
                }
                String body = (received instanceof TextMessage)
                        ? ((TextMessage) received).getText()
                        : received.toString();

                long deliveryTime = received.getJMSDeliveryTime();
                System.out.println("[" + Instant.now() + "] Received: " + body);
                if (deliveryTime > 0) {
                    System.out.println("  JMSDeliveryTime: " + Instant.ofEpochMilli(deliveryTime));
                }

                received.acknowledge();
            }

            consumer.close();
            session.close();
            connection.close();
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
