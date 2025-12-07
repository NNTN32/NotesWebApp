# 📝 NotesWebApp – Backend (Spring Boot)

Backend của ứng dụng Notes Web App, cung cấp API cho hệ thống quản lý ghi chú cá nhân.  
Hệ thống được xây dựng theo kiến trúc RESTful, đảm bảo bảo mật, dễ mở rộng và phù hợp cho môi trường sản phẩm thực tế.

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

## 🧠 Những bài toán thực tế Backend đã xử lý

### 🔐 **1. Authentication & Authorization (JWT)**
- Xây dựng luồng đăng ký – đăng nhập riêng bằng Spring Security.
- Mã hóa mật khẩu với **BCrypt**.
- Sinh & xác thực **JWT Token** để bảo vệ API.
- Tạo Security Filter để xử lý token trước khi truy cập vào controller.
- Tối ưu, phân chia lượng request thông qua Redis, Loadbalancer.
- Cân bằng chịu tải hệ thống thông qua Kafka.

### 📌 **2. CRUD ghi chú (Notes Management)**
- Tạo / sửa / xoá / lấy ghi chú theo user.
- Tự động gắn **CreatedAt – UpdatedAt**.
- Response standard: status + message + timestamp.

### 🗂️ **3. Mapping & Validation API**
- Validate input: rỗng, định dạng email, ký tự tối thiểu…
- Dùng **DTO** để tách biệt layer, tránh expose toàn bộ entity.
- Sử dụng **ModelMapper** để map giữa DTO ↔ Entity.

### ⚠️ **4. Exception Handling theo chuẩn hệ thống lớn**
- Tạo `GlobalExceptionHandler` để xử lý:
  - Invalid input
  - Unauthorized
  - Not Found
  - Duplicate email
- Format lỗi thống nhất cho FE dễ xử lý.

### 🌐 **5. CORS & giao tiếp FE–BE**
- Config CORS cho phép FE gọi API.
- Tối ưu header & method được phép gửi.

### 🧱 **6. Cấu trúc dự án chuẩn enterprise**
Giúp dễ mở rộng, dễ bảo trì, và phù hợp mô hình doanh nghiệp.

### 📑 **7. API Documentation bằng Swagger**
- Tự động generate tài liệu API cho FE sử dụng.
- Tiêu chuẩn hoá cách FE gọi API.

### 🐳 **8. Chuẩn bị sẵn cho môi trường triển khai**
- Docker hoá Backend + PostgreSQL.

---

## 👨‍💻 About Developer
**Nguyen Thanh Nhan**  
Backend Developer / Software Engineer Intern  
📧 Email: masondaniel.dev@gmail.com  
🔗 GitHub: https://github.com/NNTN32  

---
