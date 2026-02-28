# ChatWave — Real-Time Chat App

A production-ready real-time chat application built with **Spring Boot + WebSockets (STOMP)**.

## Stack
- **Backend:** Spring Boot 3.2, Spring WebSocket (STOMP), Spring Data JPA
- **Database:** H2 (in-memory, swap for PostgreSQL in production)
- **Frontend:** Vanilla JS, SockJS, STOMP.js, Thymeleaf
- **Async:** Spring `@Async` with custom thread pool for message persistence

---

## Features
| Feature | Implementation |
|---|---|
| **Multiple Rooms** | Pre-seeded: `#general`, `#tech`, `#random`, `📢 announcements` |
| **Online User Tracking** | `OnlineUserService` with `ConcurrentHashMap` + WebSocket connect/disconnect events |
| **Message Persistence** | JPA entities + H2; async persist via `@Async` thread pool so WebSocket thread isn't blocked |
| **Typing Indicator** | STOMP pub/sub on `/topic/room/{id}/typing`; debounced on client |
| **Message History** | Last 50 messages sent to user on join via `/user/queue/history` |
| **Auto-reconnect** | SockJS fallback + client-side reconnect timer |

---

## Architecture

```
Client (SockJS/STOMP)
    │
    ▼
/ws endpoint (Spring WebSocket)
    │
    ├── /app/chat.join   → ChatController.joinRoom()
    ├── /app/chat.send   → ChatController.sendMessage()
    ├── /app/chat.typing → ChatController.handleTyping()
    └── /app/chat.leave  → ChatController.leaveRoom()

Message Broker (In-Memory SimpleBroker)
    │
    ├── /topic/room/{roomId}          → All room subscribers
    ├── /topic/room/{roomId}/typing   → Typing events
    ├── /topic/room/{roomId}/users    → Online user list
    ├── /topic/online-count           → Global count
    └── /user/queue/history           → Private to joining user
```

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Run
```bash
cd chat-app
mvn spring-boot:run
```

Open → **http://localhost:8080**

### H2 Console (inspect DB)
Open → **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:chatdb`
- Username: `sa`
- Password: *(empty)*

---

## REST API

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/rooms` | List all rooms with online counts |
| GET | `/api/rooms/{id}/messages` | Last 50 messages in a room |
| GET | `/api/rooms/{id}/users` | Online users in a room |
| GET | `/api/stats` | Global stats |
| POST | `/api/rooms` | Create a new room |

---

## Production Checklist

- [ ] Swap H2 → PostgreSQL (update `application.properties`)
- [ ] Enable Spring Security for authentication
- [ ] Replace `SimpleBroker` with **RabbitMQ/ActiveMQ** for multi-node deployment
- [ ] Add message pagination (cursor-based)
- [ ] Rate limit `chat.send` endpoint
- [ ] Enable WSS (TLS) via reverse proxy (nginx)

---

## WebSocket Message Flows

### Joining a Room
```
Client                          Server
  │──── STOMP CONNECT ─────────▶│
  │◀─── CONNECTED ─────────────│
  │──── SUBSCRIBE /topic/room/{id} ──▶│
  │──── SUBSCRIBE /user/queue/history ▶│
  │──── /app/chat.join ────────▶│
  │                              │── persist JOIN msg (async)
  │◀─── /user/queue/history ───│  (last 50 msgs)
  │◀─── /topic/room/{id} ──────│  (JOIN system msg)
  │◀─── /topic/room/{id}/users │  (updated user list)
```

### Sending a Message
```
Client                          Server
  │──── /app/chat.send ────────▶│
  │                              │── persist async (thread pool)
  │◀─── /topic/room/{id} ──────│  (broadcast to all subscribers)
```
