# 💳 OfflineUPI

> **Offline UPI Payment Simulation System** built using **Spring Boot**, **Spring Data JPA**, **H2 Database**, and **AES Encryption** to simulate secure digital payments in low or no internet connectivity environments.

![Java](https://img.shields.io/badge/Java-17-red?style=for-the-badge)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?style=for-the-badge)
![Maven](https://img.shields.io/badge/Maven-Build-orange?style=for-the-badge)
![Database](https://img.shields.io/badge/Database-H2-blue?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-success?style=for-the-badge)

---

## 📌 Overview

OfflineUPI is a backend simulation of an **Offline UPI Payment System**. It demonstrates how secure payment transactions can be processed even without continuous internet connectivity.

The application encrypts payment information, stores transactions locally, simulates network availability, and synchronizes pending transactions once connectivity is restored.

---

## ✨ Features

* 💰 Offline payment simulation
* 🔒 AES encrypted transaction payload
* 🗄 H2 in-memory database
* 🌐 Network availability simulation
* 🔄 Transaction synchronization
* 📡 RESTful APIs
* 📦 Spring Data JPA
* ✅ Input validation
* 🏗 Layered Spring Boot architecture

---

## 🛠 Tech Stack

| Technology      | Purpose              |
| --------------- | -------------------- |
| Java 17         | Programming Language |
| Spring Boot     | Backend Framework    |
| Spring Data JPA | Database Access      |
| H2 Database     | In-Memory Database   |
| Maven           | Build Tool           |
| REST API        | Communication        |
| AES Encryption  | Secure Payload       |
| IntelliJ IDEA   | Development IDE      |
| Git & GitHub    | Version Control      |

---

# 📂 Project Structure

```text
src
└── main
    ├── java
    │   └── com.upi.offline
    │       ├── controller
    │       ├── dto
    │       ├── encryption
    │       ├── entity
    │       ├── repository
    │       ├── service
    │       ├── simulator
    │       └── OfflineUpiApplication.java
    │
    └── resources
        └── application.properties
```

---

# ⚙️ Installation

### Clone Repository

```bash
git clone https://github.com/anushka0809/OfflineUPI.git
```

### Open Project

```bash
cd OfflineUPI
```

### Run Application

```bash
mvn spring-boot:run
```

Application starts at

```text
http://localhost:8080
```

---

# 💾 H2 Database

Open

```text
http://localhost:8080/h2
```

Configuration

```text
JDBC URL : jdbc:h2:mem:offlineupi
Username : sa
Password : (leave empty)
```

---

# 📡 REST API

## Send Payment

**POST**

```http
/payment/send
```

Request

```json
{
    "sender":"Alice",
    "receiver":"Bob",
    "amount":500
}
```

Response

```json
{
    "status":"DELIVERED_OFFLINE",
    "hops":2
}
```

---

## Synchronize Transactions

**POST**

```http
/payment/sync
```

---

## View All Transactions

**GET**

```http
/payment/all
```

---

## View Transaction

**GET**

```http
/payment/{id}
```

---

# 🔒 Security

The project secures transaction payloads using **AES Encryption** before storing them.

Workflow:

* Payment Request
* AES Encryption
* Database Storage
* Network Simulation
* Synchronization
* Response Generation

---

# 🔄 Project Workflow

```text
User
   │
   ▼
REST Controller
   │
   ▼
Payment Service
   │
   ▼
AES Encryption
   │
   ▼
H2 Database
   │
   ▼
Network Simulator
   │
   ▼
Sync Response
```

---

# 📷 Screenshots

Create a folder named

```text
images
```

Add screenshots such as:

```text
images/
    project-structure.png
    h2-console.png
    api-testing.png
```

Then reference them like this:

```markdown
## Project Structure

![Project](images/project-structure.png)

## H2 Console

![H2](images/h2-console.png)

## API Testing

![API](images/api-testing.png)
```

---

# 🚀 Future Enhancements

* JWT Authentication
* Spring Security
* PostgreSQL/MySQL Support
* Docker Deployment
* Frontend using React
* Swagger/OpenAPI Documentation
* Unit & Integration Testing
* Redis Caching
* Kafka-based Event Processing

---

# 📈 Git Commit History

* Initial Spring Boot project setup
* Added payment request and response DTOs
* Implemented AES encryption utility
* Implemented payment service
* Added offline network simulator
* Implemented payment REST controller

---

# 👨‍💻 Author

**Anushka**

B.Tech Computer Science Engineering

Backend Developer | Java | Spring Boot | REST APIs

GitHub: https://github.com/anushka0809

---

## ⭐ If you found this project useful, don't forget to Star the repository!
