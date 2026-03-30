package com.microsoft.azure.samples.util;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.servicebus.jms.ServiceBusJmsConnectionFactory;
import com.azure.servicebus.jms.ServiceBusJmsConnectionFactorySettings;
import jakarta.jms.ConnectionFactory;

public class ConnectionHelper {

    /**
     * Creates a JMS ConnectionFactory using the best available authentication method.
     * If SERVICE_BUS_HOST is set, uses Microsoft Entra ID via DefaultAzureCredential.
     * Otherwise, falls back to connection string auth.
     */
    public static ConnectionFactory createConnectionFactory() {
        ServiceBusJmsConnectionFactorySettings settings = new ServiceBusJmsConnectionFactorySettings();

        if (Constants.SERVICE_BUS_HOST != null && !Constants.SERVICE_BUS_HOST.trim().isEmpty()) {
            return new ServiceBusJmsConnectionFactory(
                    new DefaultAzureCredentialBuilder().build(),
                    Constants.SERVICE_BUS_HOST,
                    settings);
        }
        if (Constants.SERVICE_BUS_CONNECTION_STRING != null && !Constants.SERVICE_BUS_CONNECTION_STRING.trim().isEmpty()) {
            return new ServiceBusJmsConnectionFactory(
                    Constants.SERVICE_BUS_CONNECTION_STRING, settings);
        }
        throw new IllegalStateException(
                "Set SERVICE_BUS_HOST or SERVICE_BUS_CONNECTION_STRING in Constants.java");
    }
}
