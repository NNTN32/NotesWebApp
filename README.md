# Notes Web Application

**A high-performance, real-time, event-driven Notes application** built with modern Java/Spring Boot stack, focusing on scalability, security, and reliable messaging.

**Live Demo**: Self-hosted via Cloudflare Tunnel (zero-trust networking)  
**GitHub**: [NNTN32/NotesWebApp](https://github.com/NNTN32/NotesWebApp)

## ✨ Key Features
- Modular Monolith architecture with clean separation of concerns (Auth, Notes, Media services)
- Real-time collaborative notes & instant reminder push via WebSocket (STOMP)
- Event-driven asynchronous processing (login, note updates) using Redis Streams + Kafka
- Reliable scheduled & delayed notifications with RabbitMQ (TTL + Dead Letter Exchange)
- Secure JWT authentication with HttpOnly Refresh Token rotation + suspicious reuse detection
- High-performance PostgreSQL with UUID v7 (ordered) primary keys + Idempotency guarantees

## 🛠️ Tech Stack

**Backend**  
- Java 21, Spring Boot 3.5, Spring Security, RESTful API  
- Message Broker / Event-Driven: Kafka, RabbitMQ, Redis Streams, Asynchronous Processing  
- Database & Caching: PostgreSQL, UUID v7, Redis  
- Security: JWT, OAuth2, RBAC (Role-Based Access Control)  
- Real-time: WebSocket, STOMP  

**DevOps & Deployment**  
- Docker, Docker Swarm (orchestration), Dokploy (self-hosted PaaS)  
- Cloudflare Tunnel (Zero Trust Networking)  
- CI/CD via GitHub Actions + Webhooks, Health Check Monitoring, Container Lifecycle Management  

**Concepts**  
Modular Monolith, Event-Driven Architecture (EDA), Idempotency, Scalability

## 🏗️ Architecture Highlights
- **Modular Monolith** → Clean separation of concerns, easy to maintain and scale
- **Event-Driven System** → Decoupled services, high throughput under load
- **Self-hosted Production Grade** → Docker Swarm + Cloudflare Tunnel + automated monitoring

## 🔥 Challenges & How I Solved Them

| # | Problem | Solution |
|---|---------|----------|
| 1 | API response time degraded under high load due to synchronous processing of login & note updates | Architected **event-driven system** with **Redis Streams + Kafka**, dramatically improved throughput and API responsiveness |
| 2 | Need reliable scheduled reminders + delayed message delivery without losing messages | Built robust notification system using **RabbitMQ + TTL & Dead Letter Exchange (DLX)** |
| 3 | Secure authentication & session management in distributed environment, prevent token reuse attacks | Implemented **JWT with HttpOnly Refresh Token rotation**, integrated with **Redis** for session management + suspicious reuse detection |
| 4 | Users required real-time updates for collaborative notes and reminders | Integrated **full-duplex WebSocket (STOMP)** for instant push notifications |
| 5 | Risk of duplicate data processing + inefficient primary key generation in distributed system | Used **PostgreSQL + UUID v7 (ordered)** + implemented **Idempotency logic** |
| 6 | Secure, highly available self-hosted deployment without relying on cloud vendor lock-in | Designed production-ready setup with **Docker Swarm + Dokploy + Cloudflare Tunnel** + automated CI/CD and health monitoring |

## 📦 Deployment
- Fully containerized with **Docker Swarm**
- Self-hosted PaaS using **Dokploy**
- Zero Trust Networking via **Cloudflare Tunnel**
- Automated CI/CD pipelines with **GitHub Actions**
- Health check monitoring & container lifecycle management

## 🚀 Getting Started

```bash
git clone https://github.com/NNTN32/NotesWebApp.git
cd NotesWebApp
# Chi tiết setup Docker Swarm & Dokploy nằm trong thư mục notesWeb và .github/workflows
