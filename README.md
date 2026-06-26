# OfflineUPI 💳

A Spring Boot-based Offline UPI Payment Simulation System that demonstrates how digital payments can be securely processed in low or no internet connectivity environments.

## 🚀 Features

* Offline payment request simulation
* AES encryption for secure transaction payloads
* Transaction persistence using H2 Database
* Network availability simulation
* REST APIs for payment processing
* Payment synchronization mechanism
* DTO-based request/response handling
* Spring Data JPA integration

## 🛠 Tech Stack

* Java 17
* Spring Boot
* Spring Data JPA
* Maven
* H2 Database
* REST APIs
* AES Encryption
* IntelliJ IDEA
* Git & GitHub

## 📂 Project Structure

```
src/main/java/com/upi/offline
│
├── controller
├── dto
├── encryption
├── entity
├── repository
├── service
├── simulator
└── OfflineUpiApplication.java
```

## ⚙️ Installation

Clone the repository

```bash
git clone https://github.com/anushka0809/OfflineUPI.git
```

Move into the project

```bash
cd OfflineUPI
```

Run the application

```bash
mvn spring-boot:run
```

Application runs on

```
http://localhost:8080
```

## 💾 Database

H2 Console

```
http://localhost:8080/h2
```

Database URL

```
jdbc:h2:mem:offlineupi
```

Username

```
sa
```

Password

```
(empty)
```

## 📡 API Endpoints

| Method | Endpoint      | Description                      |
| ------ | ------------- | -------------------------------- |
| POST   | /payment/send | Create an offline payment        |
| POST   | /payment/sync | Synchronize pending transactions |
| GET    | /payment/all  | View all transactions            |
| GET    | /payment/{id} | Get transaction by ID            |

## 🔒 Security

* AES Encryption
* Secure payload handling
* Encrypted transaction storage

## 📸 Sample Workflow

1. User sends payment request.
2. Request is encrypted.
3. Transaction is stored in H2 Database.
4. Network simulator determines delivery status.
5. Pending transactions are synchronized once connectivity is restored.

## 📈 Future Improvements

* JWT Authentication
* PostgreSQL/MySQL Support
* Docker Deployment
* React/Angular Frontend
* Real-time Network Monitoring
* Unit & Integration Testing

## 👨‍💻 Author

**Anushka**

Built as a backend simulation project to demonstrate secure offline UPI payment processing using Spring Boot.
