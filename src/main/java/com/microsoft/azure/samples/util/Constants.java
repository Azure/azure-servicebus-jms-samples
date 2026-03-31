package com.microsoft.azure.samples.util;

public class Constants {
    // ── Configure ONE of these ──────────────────────────────────────────────
    // Option 1 (recommended): Microsoft Entra ID authentication
    public static final String SERVICE_BUS_HOST = null; // e.g. "your-namespace.servicebus.windows.net"

    // Option 2: Connection string authentication
    public static final String SERVICE_BUS_CONNECTION_STRING = null; // e.g. "Endpoint=sb://..."

    public static final String QUEUE = "testqueue";
    public static final String SESSION_QUEUE = "sessionqueue"; // Must be created with sessions enabled (requiresSession = true)
    public static final String TOPIC = "testtopic";
}
