package com.microsoft.azure.samples;

import java.util.Random;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsQueue;

import com.microsoft.azure.samples.util.Constants;
import com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactory;
import com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactorySettings;

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
            ConnectionFactory factory = new ServiceBusJmsConnectionFactory(Constants.SERVICE_BUS_CONNECTION_STRING, new ServiceBusJmsConnectionFactorySettings());
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
                for (int j = 0; j < 10*1024; j++) {
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