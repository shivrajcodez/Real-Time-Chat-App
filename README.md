<div align="center">

# ⬡ ChatWave

**A real-time chat application built with Spring Boot and WebSockets**

[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-4A90D9?style=flat-square)](https://stomp.github.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

[Features](#-features) · [Architecture](#-architecture) · [Quick Start](#-quick-start) · [API Reference](#-api-reference) · [Production](#-going-to-production)

</div>

---

## Overview

ChatWave is a full-stack, real-time messaging app that demonstrates how to build a production-quality WebSocket system with Spring Boot. It supports multiple chat rooms, live online user tracking, persistent message history, and real-time typing indicators — all running on a single server with zero external dependencies (H2 in-memory DB, Spring's built-in STOMP broker).

The project is intentionally structured to showcase three core backend concepts: **WebSocket communication**, **asynchronous message handling**, and **real-time system design**.

---

## ✨ Features

- **Multiple chat rooms** — Pre-seeded channels (`#general`, `#tech`, `#random`, `#announcements`) with the ability to create new ones via REST
- **Online user tracking** — Per-room and global user counts updated in real time via WebSocket connect/disconnect events
- **Message persistence** — All messages stored to a JPA database, retrieved asynchronously on room join (last 50 messages)
- **Typing indicator** — Debounced, pub/sub typing events broadcast per room, auto-cleared after 2.5 seconds of inactivity
- **Message history** — Delivered privately to each user on join via a user-specific STOMP queue (not broadcast to the whole room)
- **Auto-reconnect** — SockJS fallback transport with client-side reconnect on disconnect
- **Async persistence** — Messages are broadcast to clients immediately; DB writes happen on a dedicated thread pool in the background
- **REST API** — HTTP endpoints for rooms, users, and message history

---

## 🏗 Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────┐
│                        Client                           │
│              SockJS + STOMP.js + Vanilla JS             │
└────────────────────────┬────────────────────────────────┘
                         │ WebSocket / HTTP Fallback
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    Spring Boot Server                   │
│                                                         │
│  ┌──────────────┐    ┌──────────────────────────────┐  │
│  │  REST Layer  │    │      WebSocket Layer          │  │
│  │   /api/*     │    │   /ws  (SockJS endpoint)      │  │
│  └──────────────┘    └──────────────┬───────────────┘  │
│                                     │                   │
│                      ┌──────────────▼───────────────┐  │
│                      │       ChatController          │  │
│                      │   @MessageMapping handlers    │  │
│                      └──────────────┬───────────────┘  │
│                                     │                   │
│           ┌─────────────────────────┼──────────────┐   │
│           ▼                         ▼              ▼   │
│  ┌────────────────┐  ┌──────────────────┐  ┌──────────┐│
│  │ MessageService │  │OnlineUserService │  │RoomService││
│  │ (@Async write) │  │ (ConcurrentMap)  │  │  (JPA)   ││
│  └───────┬────────┘  └──────────────────┘  └──────────┘│
│          │                                              │
│  ┌───────▼────────────────────────────────────────┐    │
│  │         In-Memory STOMP Message Broker          │    │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │               H2 Database (JPA)                 │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### STOMP Topic Map

| Destination | Direction | Purpose |
|---|---|---|
| `/app/chat.join` | Client → Server | Join a room |
| `/app/chat.send` | Client → Server | Send a message |
| `/app/chat.typing` | Client → Server | Typing status update |
| `/app/chat.leave` | Client → Server | Leave a room |
| `/topic/room/{id}` | Server → Client | Broadcast messages to a room |
| `/topic/room/{id}/typing` | Server → Client | Typing events for a room |
| `/topic/room/{id}/users` | Server → Client | Live user list for a room |
| `/topic/online-count` | Server → Client | Global online count |
| `/user/queue/history` | Server → Client | Private message history on join |
| `/user/queue/errors` | Server → Client | Private error delivery |

### Message Flows

**Joining a room:**
```
Client                                    Server
  │──── STOMP CONNECT ─────────────────▶ │
  │◀─── CONNECTED ──────────────────────  │
  │──── SUBSCRIBE /topic/room/{id} ─────▶ │
  │──── SUBSCRIBE /user/queue/history ──▶ │
  │──── /app/chat.join ─────────────────▶ │
  │                                        │── register in OnlineUserService
  │                                        │── persist JOIN msg (async)
  │◀─── /user/queue/history ────────────  │  ← last 50 msgs, private to this user
  │◀─── /topic/room/{id} ───────────────  │  ← JOIN system message (broadcast)
  │◀─── /topic/room/{id}/users ─────────  │  ← updated user list (broadcast)
```

**Sending a message:**
```
Client                                    Server
  │──── /app/chat.send ─────────────────▶ │
  │                                        │── broadcast IMMEDIATELY
  │◀─── /topic/room/{id} ───────────────  │  ← all subscribers receive it
  │                                        │── persist to DB (async, non-blocking)
```

**Typing indicator:**
```
Client A                    Server                    Client B
  │── /app/chat.typing ──▶  │                             │
  │    {typing: true}        │── /topic/room/{id}/typing ─▶ │
  │                          │                             │
  │  [2.5s no input]         │                             │
  │── /app/chat.typing ──▶  │                             │
  │    {typing: false}       │── /topic/room/{id}/typing ─▶ │
```

---

## 📁 Project Structure

```
src/main/java/com/chatapp/
├── ChatApplication.java              # Entry point (@EnableAsync)
│
├── config/
│   ├── WebSocketConfig.java          # STOMP broker + SockJS endpoint
│   ├── AsyncConfig.java              # Thread pool for async DB writes
│   ├── WebSocketEventListener.java   # Connect/disconnect event hooks
│   └── DataInitializer.java          # Seeds rooms + welcome messages on startup
│
├── model/
│   ├── Message.java                  # JPA entity (CHAT / JOIN / LEAVE / SYSTEM)
│   ├── ChatRoom.java                 # JPA entity
│   ├── UserSession.java              # In-memory only, not persisted
│   └── ChatDTOs.java                 # All WebSocket payload DTOs (inbound + outbound)
│
├── repository/
│   ├── MessageRepository.java        # findLastMessagesByRoomId w/ Pageable
│   └── ChatRoomRepository.java
│
├── service/
│   ├── MessageService.java           # @Async saveMessageAsync + history fetch
│   ├── RoomService.java              # Room CRUD, default channel seeding
│   └── OnlineUserService.java        # Thread-safe ConcurrentHashMap of live sessions
│
└── controller/
    ├── ChatController.java           # All @MessageMapping WebSocket handlers
    ├── RoomController.java           # REST /api/* endpoints
    └── HomeController.java           # Serves the SPA shell

src/main/resources/
├── application.properties
├── templates/index.html              # Thymeleaf template (SPA shell)
└── static/
    ├── css/style.css                 # Dark terminal-inspired UI
    └── js/chat.js                    # STOMP client, typing debounce, DOM rendering
```

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** — [Download OpenJDK](https://adoptium.net/)
- **Maven 3.8+** — [Download](https://maven.apache.org/download.cgi)

### Run locally

```bash
# Clone the repo
git clone https://github.com/your-username/chatwave.git
cd chatwave

# Run
mvn spring-boot:run
```

Open **http://localhost:8080**, pick a username, and start chatting. Open a second browser tab to simulate a second user.

### Inspect the database

The H2 console is available at **http://localhost:8080/h2-console** while the app is running.

```
JDBC URL:  jdbc:h2:mem:chatdb
Username:  sa
Password:  (leave blank)
```

### Build a JAR

```bash
mvn clean package -DskipTests
java -jar target/realtime-chat-1.0.0.jar
```

---

## 📡 API Reference

### Rooms

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/rooms` | List all rooms with live online counts |
| `POST` | `/api/rooms` | Create a new room |
| `GET` | `/api/rooms/{id}/messages` | Last 50 messages in a room |
| `GET` | `/api/rooms/{id}/users` | Online users currently in a room |
| `GET` | `/api/stats` | Global stats (total online, room count) |

**Create a room:**
```bash
curl -X POST http://localhost:8080/api/rooms \
  -H "Content-Type: application/json" \
  -d '{"name": "#design", "description": "UI/UX and design discussion"}'
```

**Get recent messages:**
```bash
curl http://localhost:8080/api/rooms/general/messages
```

---

## ⚙️ Configuration

Key settings in `src/main/resources/application.properties`:

```properties
server.port=8080

# H2 in-memory (swap for PostgreSQL in production)
spring.datasource.url=jdbc:h2:mem:chatdb
spring.h2.console.enabled=true

# JPA
spring.jpa.hibernate.ddl-auto=create-drop
```

### Switching to PostgreSQL

Replace the H2 dependency in `pom.xml`:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Update `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/chatwave
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

---

## 🏭 Going to Production

The app runs fine out of the box for demos and single-node deployments. For production, work through this checklist:

**Infrastructure**
- [ ] Replace H2 with **PostgreSQL** (see above)
- [ ] Replace `SimpleBroker` with **RabbitMQ or ActiveMQ** for multi-node support — Spring's STOMP broker relay makes this a config-only change
- [ ] Put the app behind **nginx** with WebSocket proxying and TLS (WSS)

**Security**
- [ ] Add **Spring Security** — protect HTTP endpoints and validate auth tokens on WebSocket connect
- [ ] Sanitize message content server-side (basic XSS escaping is already in `ChatController.sanitize()`)
- [ ] Rate-limit `/app/chat.send` per user

**Reliability**
- [ ] Add cursor-based **message pagination** (the `Pageable` pattern in `MessageRepository` is already set up for this)
- [ ] Store `UserSession` in **Redis** for recovery across server restarts
- [ ] Add health and metrics endpoints via **Spring Actuator**

**Multi-node STOMP relay (RabbitMQ)** — swap this into `WebSocketConfig.java`:

```java
// Replace enableSimpleBroker() with:
config.enableStompBrokerRelay("/topic", "/queue")
      .setRelayHost("localhost")
      .setRelayPort(61613)
      .setClientLogin("guest")
      .setClientPasscode("guest");
```

---

## 🔧 Key Design Decisions

**Why async persistence?**
WebSocket handlers run on a shared thread pool. Blocking on a DB write for every message degrades throughput under load. `MessageService.saveMessageAsync()` uses `@Async` with a dedicated `ThreadPoolTaskExecutor` — messages are broadcast to clients first, and the DB write follows in the background via `CompletableFuture`.

**Why `ConcurrentHashMap` for online users?**
WebSocket connect/disconnect events fire from multiple threads. `ConcurrentHashMap` gives lock-free reads and fine-grained locking on writes — a good fit for a structure that's read constantly but written infrequently.

**Why user-private queues for history?**
Message history is sent to `/user/queue/history` — a STOMP destination scoped to a single session. Publishing history to the room topic would re-broadcast it to all active users in the room, which would be wrong. Spring's `convertAndSendToUser()` handles the session scoping transparently.

**Why SockJS?**
SockJS provides automatic fallback to HTTP long-polling when WebSockets are blocked (corporate proxies, firewalls). It's transparent to the STOMP layer and adds resilience for no cost.

---

## 📄 License

MIT — use it however you like.

---

<div align="center">
Built with Spring Boot · WebSocket · STOMP · SockJS · H2
</div>
