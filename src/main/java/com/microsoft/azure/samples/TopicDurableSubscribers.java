package com.microsoft.azure.samples;

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
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.qpid.jms.JmsTopic;

import com.microsoft.azure.samples.util.Constants;
import com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactory;
import com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactorySettings;

public class TopicDurableSubscribers {
    private static final int DEFAULT_COUNT = 10;
    private static final int DELIVERY_MODE = DeliveryMode.PERSISTENT;

    public static void main(String[] args) throws Exception {
        int count = DEFAULT_COUNT;
        if (args.length == 0) {
            System.out.println("Sending up to " + count + " messages.");
            System.out.println("Specify a message count as the program argument if you wish to send a different amount.");
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
            
            Destination dest = new JmsTopic(Constants.TOPIC);

            connection.setClientID("this-sample");
            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);


            /*
             * Sending
             */

            Topic topic = session.createTopic(dest.toString());
            MessageProducer sender = session.createProducer(dest);
            String sub1Name = dest.toString()+"_sub1";
            String sub2Name = dest.toString()+"_sub2";
            String sub3Name = dest.toString()+"_sub3";
            TopicSubscriber topicSubscriber1 = session.createDurableSubscriber(topic, sub1Name);
            TopicSubscriber topicSubscriber2 = session.createDurableSubscriber(topic, sub2Name, "JMSCorrelationID='5'", false);
            MessageConsumer topicSubscriber3 = session.createSharedDurableConsumer(topic, sub3Name, "JMSCorrelationID='5'");

            long start = System.currentTimeMillis();
            for (int i = 1; i <= count; i++) {
                TextMessage message = session.createTextMessage("Text!");
                message.setJMSCorrelationID(Integer.toString(i));
                message.setBooleanProperty("prop1", true);
                sender.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

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

     
            receive(count, topicSubscriber1);
            receive(count, topicSubscriber2);
            receive(count, topicSubscriber3);

            topicSubscriber1.close();
            topicSubscriber2.close();
            topicSubscriber3.close();

            /*
             * The unsubscribe() call will delete the subscriptions. 
             * To view the durable subscriptions created by this program, comment out these lines. 
             */
            session.unsubscribe(sub1Name);
            session.unsubscribe(sub2Name);
            session.unsubscribe(sub3Name);

            connection.close();
        } catch (Exception exp) {
            System.out.println("Caught exception, exiting.");
            exp.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void receive(int count, MessageConsumer topicSubscriber) throws JMSException {
        long start;
        long finish;
        long taken;
        start = System.currentTimeMillis();

        int actualCount = 0;
        boolean deductTimeout = false;
        int timeout = 1000;
        for (int i = 1; i <= count; i++, actualCount++) {
            Message message = topicSubscriber.receive(timeout);
            if (message == null) {
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
    }

    private static class MyExceptionListener implements ExceptionListener {
        public void onException(JMSException exception) {
            System.out.println("Connection ExceptionListener fired, exiting.");
            exception.printStackTrace(System.out);
            System.exit(1);
        }
    }
}