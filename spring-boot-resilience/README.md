# Spring Boot JMS Resilience Sample

Demonstrates correct connection factory configuration for Azure Service Bus JMS
with resilient listeners and efficient senders.

This sample addresses the #1 customer pain point with JMS on Service Bus:
**listeners going silent and not recovering after token expiry or connection
drops.** The root cause is using the wrong connection factory wrapper for
listener containers.

## Architecture

The sample uses **two different connection factory configurations** for senders
and listeners:

```
┌──────────────────────────────────────────────────────────────────┐
│  Spring Boot Application                                         │
│                                                                  │
│  ┌─────────────────────────┐   ┌──────────────────────────────┐ │
│  │  MessageSender          │   │  MessageListener             │ │
│  │  (JmsTemplate)          │   │  (@JmsListener)              │ │
│  └───────────┬─────────────┘   └──────────────┬───────────────┘ │
│              │                                │                  │
│  ┌───────────▼─────────────┐   ┌──────────────▼───────────────┐ │
│  │  CachingConnectionFactory│  │  ServiceBusJmsConnectionFactory│
│  │  (caches connections    │   │  (raw — each listener gets   │ │
│  │   and sessions)         │   │   its own AMQP connection)   │ │
│  └───────────┬─────────────┘   └──────────────┬───────────────┘ │
│              │                                │                  │
└──────────────┼────────────────────────────────┼──────────────────┘
               │                                │
               ▼                                ▼
     ┌─────────────────────────────────────────────┐
     │  Azure Service Bus (Premium)                 │
     └─────────────────────────────────────────────┘
```

### Why separate factories?

| Role | Factory | Reason |
|------|---------|--------|
| **Senders** | `CachingConnectionFactory` | Reuses connections and sessions across sends. Without caching, `JmsTemplate` creates and closes a connection per send, which exhausts the 256 AMQP link limit under load. |
| **Listeners** | Raw `ServiceBusJmsConnectionFactory` | Each listener container gets its own AMQP connection with independent lifecycle. If one connection fails (token expiry, gateway upgrade), only that listener is affected, and Spring recreates the connection automatically. |

### What NOT to use for listeners

**Never use `SingleConnectionFactory` for listener containers.** It forces all
listeners to share a single JMS connection. When that connection enters a CLOSED
state after token expiry:

1. Listener threads block in `createSession()` on the closed connection.
2. No exception is surfaced to Spring's `DefaultMessageListenerContainer`.
3. Recovery logic never triggers — all listeners become "zombie listeners."
4. The application appears running, but no messages are consumed.
5. The only fix is a restart.

This exact failure mode caused a multi-month production escalation where JMS
listeners went silent for days without recovery.

## Spring Properties Reference

This sample configures factories explicitly in `JmsConfig.java`. The table below
shows the equivalent behavior when using `spring-cloud-azure-starter-servicebus-jms`
(v5.22+) with property-based configuration:

| `pool.enabled` | `cache.enabled` | Sender | Listener |
|:---------------|:----------------|:-------|:---------|
| *(not set)* | *(not set)* | **CachingConnectionFactory** | **ServiceBusJmsConnectionFactory** |
| *(not set)* | `true` | CachingConnectionFactory | CachingConnectionFactory |
| *(not set)* | `false` | ServiceBusJmsConnectionFactory | ServiceBusJmsConnectionFactory |
| `true` | *(not set)* | JmsPoolConnectionFactory | JmsPoolConnectionFactory |

The default row (both unset) matches what this sample configures explicitly.

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Azure Service Bus Premium namespace** (JMS 2.0 requires Premium tier)
- A queue named `testqueue` (configurable via `sample.queue-name` property)

## Setup

### Option 1: Connection string authentication

```bash
export SERVICEBUS_CONNECTION_STRING="Endpoint=sb://your-namespace.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=..."
```

### Option 2: Entra ID (passwordless) authentication

```bash
export SERVICEBUS_NAMESPACE="your-namespace"
```

This uses `DefaultAzureCredential`, which automatically picks up credentials from
Managed Identity, Azure CLI, IntelliJ, VS Code, and other sources.

## Build and Run

```bash
cd spring-boot-resilience
mvn clean package
java -jar target/spring-boot-jms-resilience-0.0.1-SNAPSHOT.jar
```

The application sends a test message every 10 seconds and listens for messages
on the configured queue. Watch the console for send/receive logs and any
connection error events.

## Key Files

| File | Purpose |
|------|---------|
| `JmsConfig.java` | **The core of the sample.** Creates the raw factory, `CachingConnectionFactory` for senders, listener container factory with recovery settings, and the `ExceptionListener` for observability. |
| `MessageSender.java` | Sends messages via `JmsTemplate` with cached connections. |
| `MessageListener.java` | Receives messages via `@JmsListener` with error handling that separates broker errors from application errors. |
| `application.yml` | Connection properties, queue name, and logging configuration. |

## Known Pitfalls

1. **Using the same factory for senders and listeners.** Senders need caching
   for efficiency; listeners need raw connections for resilience. The old Spring
   Cloud Azure default (pre-5.22) used the same factory for both.

2. **Missing exception listener.** Without one, connection drops are completely
   silent — no log entry, no metric, no alert. The only symptom is that messages
   stop being consumed.

3. **Token expiry varies by platform.** On Windows, Entra ID tokens expire at
   ~1 hour with reliable reconnection. On Linux (Managed Identity), tokens last
   ~24 hours, and the reconnection failure is harder to reproduce but more
   severe when it occurs.

4. **`SingleConnectionFactory` for listeners.** Causes zombie listeners (see
   "What NOT to use" above). This is the most dangerous misconfiguration.

5. **`JmsPoolConnectionFactory` for listeners.** Pooled connections can become
   stale. If you must pool, ensure health-check and eviction settings are
   properly configured.
