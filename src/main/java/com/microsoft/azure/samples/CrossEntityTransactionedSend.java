package com.microsoft.azure.samples;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import com.microsoft.azure.samples.util.Constants;
import com.microsoft.azure.servicebus.jms.ServiceBusJmsConnectionFactory;

public class CrossEntityTransactionedSend {

	public static void main() throws JMSException {
		String queueName = "MyQueue";
		String queueName2 = "MyQueue2";
		ConnectionFactory factory = new ServiceBusJmsConnectionFactory(Constants.SERVICE_BUS_CONNECTION_STRING, null);
		Connection connection = null; 

		try {
			connection = factory.createConnection();
			connection.start();

			// This creates the transacted session from the service
			Session transactedSession = connection.createSession(true, Session.SESSION_TRANSACTED);

			Queue queue = transactedSession.createQueue(queueName);
			// Creating this producer will create the queue from the service side, and will establish it as the "root" entity implicitly
			// Creating the consumer with this queue here will also establish the queue as the "root" entity implicitly
			System.out.println("Creating the transacted producer for " + queueName + ", establishing it as the root entity for this transaction");
			MessageProducer producer = transactedSession.createProducer(queue);

			// Create a different producer under a different queue with the same transacted session
			// Messages sent through this producer2 will be sent "root" the queue established earlier
			Queue queue2 = transactedSession.createQueue(queueName2);
			System.out.println("Creating the transacted producer for " + queueName2);
			MessageProducer producer2 = transactedSession.createProducer(queue2);

			// Creating non transacted consumers to verify the messages
			System.out.println("Creating non transacted session and consumers to verify the messages.");
			Session nonTransactedSession = connection.createSession();
			MessageConsumer consumer = nonTransactedSession.createConsumer(queue);
			MessageConsumer consumer2 = nonTransactedSession.createConsumer(queue2);

			// Send messages and commit the session
			System.out.println("Sending message to both queues.");
			producer.send(transactedSession.createTextMessage("message for " + queueName));
			producer2.send(transactedSession.createTextMessage("message for " + queueName2));
			transactedSession.commit();

			// Receive the messages to verify
			System.out.println("Receiving the messages to verify.");
			Message received = consumer.receive(2000);
			Message received2 = consumer2.receive(2000);

			if (received != null) {
				System.out.println("Received message from " + queueName);
			} else {
				System.err.println("Did not receive message from " + queueName);
			}

			if (received2 != null) {
				System.out.println("Received message from " + queueName2);
			} else {
				System.err.println("Did not receive message from " + queueName2);
			}
		} finally {
			connection.close();
		}
	}

}