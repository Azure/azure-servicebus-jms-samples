package com.microsoft.azure.samples;

import java.util.Random;

import jakarta.jms.BytesMessage;
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

import org.apache.qpid.jms.JmsQueue;

import com.microsoft.azure.samples.util.Constants;

public class QueueReceive10MB {
    private static final int DEFAULT_COUNT = 10;
    private static final int DELIVERY_MODE = DeliveryMode.PERSISTENT;

    public static void main(String[] args) throws Exception {
        
        Random rnd = new Random();
        int count = DEFAULT_COUNT;
        if (args.length == 0) {
            System.out.println("Sending up to " + count + " messages.");
            System.out
                    .println("Specify a message count as the program argument if you wish to send a different amount.");
        } else {
            count = Integer.parseInt(args[0]);
            System.out.println("Sending up to " + count + " messages.");
        }

        try {
        	/*
        	 * Initialize the JMS Connection and Session.
        	 */
            ConnectionFactory factory = Constants.createConnectionFactory();
            Connection connection = factory.createConnection();

            Destination queue = new JmsQueue(Constants.QUEUE);

            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            /*
             * Sending
             */

            MessageProducer messageProducer = session.createProducer(queue);

            long start = System.currentTimeMillis();
            for (int i = 1; i <= count; i++) {
                BytesMessage message = session.createBytesMessage();
                // Write slightly under 10MB to leave room for AMQP protocol framing overhead (~200 bytes)
                int totalChunks = 10 * 1024 - 1; // 10,484,736 bytes
                for (int j = 0; j < totalChunks; j++) {
                    byte[] bytes = new byte[1024];
                    rnd.nextBytes(bytes);
                    message.writeBytes(bytes); 
                } 
                
                messageProducer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                if (i % 100 == 0) {
                    System.out.println("Sent message " + i);
                }
            }

            long finish = System.currentTimeMillis();
            long taken = finish - start;
            System.out.println("Sent " + count + " messages in " + taken + "ms");

            /*
             * Receiving
             */

            MessageConsumer messageConsumer = session.createConsumer(queue);

            start = System.currentTimeMillis();

            int actualCount = 0;
            boolean deductTimeout = false;
            int timeout = 1000;
            for (int i = 1; i <= count; i++, actualCount++) {
                Message message = messageConsumer.receive(timeout);
                if (message == null) {
                    System.out.println("Message " + i + " not received within timeout, stopping.");
                    deductTimeout = true;
                    break;
                }
                message.acknowledge();
                if (i % 100 == 0) {
                    System.out.println("Got message " + i);
                }
            }

            finish = System.currentTimeMillis();
            taken = finish - start;
            if (deductTimeout) {
                taken -= timeout;
            }
            System.out.println("Received " + actualCount + " messages in " + taken + "ms");

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