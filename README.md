# 📝 NotesWebApp – Backend (Spring Boot)

The backend service of the Notes Web App, providing a secure and scalable RESTful API for managing personal notes.
This project is designed with production-oriented architecture, focusing on modularity, maintainability, and real-world problem-solving.

---

## 🔗 Frontend Repository
ReactJS Frontend:  
👉 https://github.com/NNTN32/NotesWeb_FE

---

## 🚀 Tech Stack (Backend)

### **Core Technologies**
- **Java 21**
- **Spring Boot 3**
- **Spring Web (REST API)**
- **Spring Data JPA**
- **Spring Security + JWT Authentication**
- **Hibernate ORM**
- **Lombok**
- **ModelMapper**
- **Validation (Jakarta Validation)**

### **Database & Infrastructure**
- **PostgreSQL**
- **Docker & Docker Compose**
- **Swagger OpenAPI v3**
- **pgAdmin / DBeaver**

---

## 🧠 Real-World Problems Addressed by Backend

### 🔐 **1. Authentication & Authorization (JWT)**
	•	Implemented fully custom register & login flows with Spring Security.
	•	Encrypted passwords using BCrypt.
	•	Generated & validated JWT tokens for secure API access.
	•	Built a custom Security Filter to process tokens before controller access.
	•	Optimized request distribution via Redis and Load Balancer.
	•	Improved system scalability by integrating Kafka for asynchronous load handling.

### 📌 **2. Notes Management (CRUD Operations)**
	•	Create / update / delete / fetch notes per user.
	•	Automatic CreatedAt & UpdatedAt timestamps.
	•	Standardized API responses for consistency (status + message + timestamp).

### 🗂️ **3. DTO Mapping & Input Validation**
	•	Applied strict validation: non-empty fields, email format, min/max length.
	•	Used DTOs to separate API models from database entities.
	•	Utilized ModelMapper for efficient mapping between DTO ↔ Entity.

### ⚠️ **4. Centralized Exception Handling**
- Created a GlobalExceptionHandler to manage:
	•	Invalid input
	•	Unauthorized access
	•	Entity not found
	•	Duplicate account / resource
→ Ensures consistent error format for frontend processing.

### 🌐 **5. CORS & Frontend Communication**
	•	Configured flexible CORS for smooth FE–BE communication.
	•	Optimized allowed headers, exposed headers, and supported HTTP methods.

### 🧱 **6. Enterprise-Level Project Structure**
A clean architecture ready for scaling in real-world systems.

### 📑 **7. API Documentation bằng Swagger**
	•	Auto-generated OpenAPI documentation.
	•	Helps frontend developers quickly understand API usage.

### 🐳 **8. Deployment-Ready Infrastructure**
	•	Containerized Backend + PostgreSQL using Docker.
	•	Environment separation for local, dev, and production.

---

## 👨‍💻 About Developer
**Nhan Nguyen**  
Backend Engineer/ Java Software Engineer

📧 Email: nhannguyendev371@gmail.com  
🔗 GitHub: https://github.com/NNTN32  

---
