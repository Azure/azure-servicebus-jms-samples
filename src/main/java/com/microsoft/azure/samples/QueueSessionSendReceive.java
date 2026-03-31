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
 * Demonstrates sending and receiving session-aware messages on an Azure Service
 * Bus queue using JMS 2.0. Messages within the same session are delivered in
 * FIFO order; different sessions are independent of each other.
 *
 * Prerequisites:
 *   - The queue referenced by Constants.SESSION_QUEUE must be created with
 *     sessions enabled (requiresSession = true) in the Azure portal or via
 *     ARM/Bicep. This cannot be configured through JMS.
 */
public class QueueSessionSendReceive {
    private static final int DELIVERY_MODE = DeliveryMode.PERSISTENT;
    private static final int MESSAGES_PER_SESSION = 10;
    private static final String[] SESSION_IDS = {"session-A", "session-B", "session-C"};

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

            // The queue must be created with sessions enabled in the Azure portal.
            // For example: "sessionqueue" (configured with requiresSession = true).
            JmsQueue queue = new JmsQueue(Constants.SESSION_QUEUE);

            /*
             * Sending — distribute messages across three session IDs.
             * Setting JMSXGroupID assigns each message to a session.
             */
            MessageProducer producer = session.createProducer(queue);
            int totalSent = 0;

            long start = System.currentTimeMillis();
            for (String sessionId : SESSION_IDS) {
                for (int i = 1; i <= MESSAGES_PER_SESSION; i++) {
                    TextMessage message = session.createTextMessage(
                            "Session [" + sessionId + "] message " + i);
                    message.setStringProperty("JMSXGroupID", sessionId);
                    producer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY,
                            Message.DEFAULT_TIME_TO_LIVE);
                    totalSent++;
                }
                System.out.println("Sent " + MESSAGES_PER_SESSION
                        + " messages to session [" + sessionId + "]");
            }
            long taken = System.currentTimeMillis() - start;
            System.out.println("Sent " + totalSent + " messages across "
                    + SESSION_IDS.length + " sessions in " + taken + "ms");
            System.out.println();

            /*
             * Receiving — create consumers that receive from the session-enabled
             * queue. The broker assigns the next available session to each
             * consumer. Messages within a session arrive in FIFO order.
             */
            for (int s = 0; s < SESSION_IDS.length; s++) {
                MessageConsumer consumer = session.createConsumer(queue);

                System.out.println("Receiving from session " + (s + 1)
                        + " of " + SESSION_IDS.length + " ...");

                int received = 0;
                start = System.currentTimeMillis();

                while (true) {
                    Message message = consumer.receive(2000);
                    if (message == null) {
                        break;
                    }
                    received++;
                    if (message instanceof TextMessage) {
                        String body = ((TextMessage) message).getText();
                        String receivedSessionId = message.getStringProperty("JMSXGroupID");
                        System.out.println("  [" + receivedSessionId + "] " + body);
                    }
                    message.acknowledge();
                }
                taken = System.currentTimeMillis() - start - 2000;
                System.out.println("Received " + received + " messages in "
                        + Math.max(taken, 0) + "ms");
                System.out.println();

                consumer.close();
            }

            connection.close();
            System.out.println("Done.");
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
