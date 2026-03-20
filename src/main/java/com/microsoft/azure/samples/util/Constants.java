package com.microsoft.azure.samples.util;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.servicebus.jms.ServiceBusJmsConnectionFactory;
import com.azure.servicebus.jms.ServiceBusJmsConnectionFactorySettings;
import jakarta.jms.ConnectionFactory;

public class Constants {
    // Option 1: Connection string authentication
    public static final String SERVICE_BUS_CONNECTION_STRING = "<YOUR_SERVICEBUS_CONNECTION_STRING>";

    // Option 2: Microsoft Entra ID authentication (recommended for production)
    // Set this to your namespace host, e.g. "your-namespace.servicebus.windows.net"
    public static final String SERVICE_BUS_HOST = "<YOUR_NAMESPACE>.servicebus.windows.net";

    public static final String QUEUE = "testqueue";
    public static final String TOPIC = "testtopic";

    /**
     * Creates a JMS ConnectionFactory using the best available authentication method.
     * If SERVICE_BUS_HOST is set, uses Microsoft Entra ID via DefaultAzureCredential.
     * Otherwise, falls back to connection string auth.
     */
    public static ConnectionFactory createConnectionFactory() {
        ServiceBusJmsConnectionFactorySettings settings = new ServiceBusJmsConnectionFactorySettings();

        if (isConfigured(SERVICE_BUS_HOST)) {
            // Microsoft Entra ID authentication (recommended for production)
            return new ServiceBusJmsConnectionFactory(
                    new DefaultAzureCredentialBuilder().build(),
                    SERVICE_BUS_HOST,
                    settings);
        } else if (isConfigured(SERVICE_BUS_CONNECTION_STRING)) {
            // Connection string authentication
            return new ServiceBusJmsConnectionFactory(SERVICE_BUS_CONNECTION_STRING, settings);
        } else {
            throw new IllegalStateException(
                    "Configure either SERVICE_BUS_HOST (recommended) or SERVICE_BUS_CONNECTION_STRING in Constants.java");
        }
    }

    private static boolean isConfigured(String value) {
        if (value == null || value.isEmpty() || value.startsWith("<")) {
            return false;
        }
        // Connection strings start with "Endpoint=sb://"
        if (value.contains("Endpoint=sb://")) {
            return value.contains("SharedAccessKeyName=") || value.contains("SharedAccessSignature=");
        }
        // Host names must end with ".servicebus.windows.net"
        return value.endsWith(".servicebus.windows.net");
    }
}
