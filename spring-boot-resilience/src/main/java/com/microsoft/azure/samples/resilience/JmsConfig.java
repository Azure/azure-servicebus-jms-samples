package com.microsoft.azure.samples.resilience;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.servicebus.jms.ServiceBusJmsConnectionFactory;
import com.azure.servicebus.jms.ServiceBusJmsConnectionFactorySettings;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * Connection factory configuration for Azure Service Bus JMS.
 *
 * <h2>Architecture: separate factories for senders and listeners</h2>
 *
 * <p><b>Senders</b> use {@link CachingConnectionFactory}, which maintains a single
 * AMQP connection and caches sessions across sends. Without caching,
 * {@link JmsTemplate} creates and closes a connection per send — at volume this
 * exhausts broker resources under load.</p>
 *
 * <p><b>Listeners</b> use the raw {@link ServiceBusJmsConnectionFactory} directly.
 * Each listener container opens its own AMQP connection with independent lifecycle.
 * When a connection fails (token expiry, gateway upgrade), only that listener is
 * affected, and Spring recreates the connection automatically.</p>
 *
 * <h2>What NOT to use for listeners</h2>
 *
 * <p>Never use {@code SingleConnectionFactory} with listener containers. It forces
 * all listeners to share a single JMS connection. When that connection enters a
 * CLOSED state after token expiry, listener threads block in
 * {@code createSession()} with no exception surfaced to Spring's recovery
 * mechanism. All listeners become unresponsive ("zombie listeners") with no
 * automatic recovery — the application must be restarted.</p>
 *
 * <p>This zombie listener condition was the root cause of a multi-month production
 * escalation where JMS listeners went silent for days. The fix: use the raw
 * factory for listeners so each gets its own connection.</p>
 *
 * <h2>Spring Cloud Azure equivalent</h2>
 *
 * <p>If you use {@code spring-cloud-azure-starter-servicebus-jms} (version 6.2.0+),
 * the starter applies this same factory separation by default. This sample shows
 * the explicit configuration for users who need direct control or are on older
 * versions.</p>
 */
@Configuration
public class JmsConfig {

    private static final Logger log = LoggerFactory.getLogger(JmsConfig.class);

    // -----------------------------------------------------------------------
    // Base factory — raw ServiceBusJmsConnectionFactory
    // -----------------------------------------------------------------------

    /**
     * Creates the base Azure Service Bus JMS connection factory.
     *
     * <p>Supports two authentication modes:</p>
     * <ul>
     *   <li><b>Connection string</b>: Set {@code SERVICEBUS_CONNECTION_STRING}
     *       environment variable.</li>
     *   <li><b>Entra ID (passwordless)</b>: Set {@code SERVICEBUS_NAMESPACE}
     *       environment variable. Uses {@code DefaultAzureCredential} which
     *       supports Managed Identity, Azure CLI, IntelliJ, VS Code, and other
     *       credential sources.</li>
     * </ul>
     *
     * <p>The factory's built-in reconnect settings default to unlimited retries
     * with exponential backoff (initial: 10ms, max: 30s, multiplier: 2.0),
     * which is appropriate for production use.</p>
     */
    @Bean
    public ServiceBusJmsConnectionFactory serviceBusJmsConnectionFactory(
            @Value("${servicebus.connection-string:}") String connectionString,
            @Value("${servicebus.namespace:}") String namespace) {

        ServiceBusJmsConnectionFactorySettings settings =
                new ServiceBusJmsConnectionFactorySettings();

        if (connectionString != null && !connectionString.isBlank()) {
            log.info("Connecting to Service Bus using connection string");
            return new ServiceBusJmsConnectionFactory(connectionString, settings);
        }

        if (namespace != null && !namespace.isBlank()) {
            // The fully qualified namespace typically looks like:
            // your-namespace.servicebus.windows.net
            String host = namespace.contains(".")
                    ? namespace
                    : namespace + ".servicebus.windows.net";
            log.info("Connecting to Service Bus namespace '{}' using Entra ID", host);
            return new ServiceBusJmsConnectionFactory(
                    new DefaultAzureCredentialBuilder().build(), host, settings);
        }

        throw new IllegalStateException(
                "Set servicebus.connection-string or servicebus.namespace property "
                + "to connect to Azure Service Bus (e.g. via SERVICEBUS_CONNECTION_STRING "
                + "or SERVICEBUS_NAMESPACE environment variable)");
    }

    // -----------------------------------------------------------------------
    // Sender configuration — CachingConnectionFactory
    // -----------------------------------------------------------------------

    /**
     * Wraps the raw factory in a {@link CachingConnectionFactory} for efficient
     * sending. The cache maintains a single AMQP connection and reuses JMS
     * sessions, avoiding the create/close-per-send overhead.
     *
     * <p>Marked {@code @Primary} so any auto-wired {@code ConnectionFactory}
     * injection (including Spring's default {@link JmsTemplate}) uses this
     * cached variant.</p>
     *
     * <p>The session cache size controls how many JMS sessions are kept open.
     * Increase this if the application sends from many threads concurrently.</p>
     */
    @Bean
    @Primary
    public CachingConnectionFactory senderConnectionFactory(
            ServiceBusJmsConnectionFactory rawFactory) {
        CachingConnectionFactory factory = new CachingConnectionFactory(rawFactory);
        factory.setSessionCacheSize(10);
        factory.setReconnectOnException(true);
        factory.setExceptionListener(new ConnectionExceptionListener("sender"));
        return factory;
    }

    /**
     * JMS template for sending messages, backed by the cached connection factory.
     */
    @Bean
    public JmsTemplate jmsTemplate(CachingConnectionFactory senderConnectionFactory) {
        JmsTemplate template = new JmsTemplate(senderConnectionFactory);
        template.setExplicitQosEnabled(true);
        template.setDeliveryPersistent(true);
        return template;
    }

    // -----------------------------------------------------------------------
    // Listener configuration — raw ServiceBusJmsConnectionFactory
    // -----------------------------------------------------------------------

    /**
     * Listener container factory using the raw (un-cached) connection factory.
     *
     * <p>Each {@code @JmsListener} backed by this factory gets its own AMQP
     * connection. If a connection drops (token expiry, network blip, gateway
     * upgrade), Spring's {@code DefaultMessageListenerContainer} catches the
     * exception and reconnects after the configured recovery interval — without
     * affecting other listeners.</p>
     *
     * <p>The 5-second recovery interval balances fast recovery against
     * reconnection storm risk during extended outages.</p>
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ServiceBusJmsConnectionFactory rawFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(rawFactory);
        factory.setRecoveryInterval(5000L);
        factory.setSessionTransacted(false);
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setExceptionListener(new ConnectionExceptionListener("listener"));
        return factory;
    }

    // -----------------------------------------------------------------------
    // Exception listener — observability for connection failures
    // -----------------------------------------------------------------------

    /**
     * Logs JMS connection errors for observability.
     *
     * <p>This listener does <b>not</b> perform recovery — Spring's listener
     * container and the {@link CachingConnectionFactory} handle reconnection
     * automatically. The purpose is pure observability: without an exception
     * listener, connection drops are silent. It becomes impossible to distinguish
     * "no messages available" from "connection is dead."</p>
     *
     * <p>In production, wire this to your monitoring framework (metrics counter,
     * alert trigger, health check degradation).</p>
     */
    static class ConnectionExceptionListener implements ExceptionListener {

        private static final Logger listenerLog =
                LoggerFactory.getLogger(ConnectionExceptionListener.class);

        private final String role;

        ConnectionExceptionListener(String role) {
            this.role = role;
        }

        @Override
        public void onException(JMSException exception) {
            listenerLog.error("JMS connection error [{}]: {}",
                    role, exception.getMessage(), exception);
        }
    }
}
