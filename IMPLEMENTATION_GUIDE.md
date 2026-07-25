# Order Service REST API Implementation

## Overview

This document describes the comprehensive REST API implementation for the Order Service with the following features:

- **POST Endpoint** at `/service/orders` for creating orders
- **Multi-Service Validation** using Spring Cloud Feign clients
- **Database Persistence** with JPA/Hibernate and H2
- **Event-Driven Architecture** with Kafka message publishing
- **Invoice Generation** and event publishing upon order creation

---

## Implementation Details

### 1. REST API Endpoint

**Endpoint:** `POST /service/orders`

**Request Body (JSON):**
```json
{
  "customerId": 123,
  "orderDesc": "Electronics Order",
  "orderDate": "2026-07-25",
  "items": [
    {
      "itemName": "Laptop",
      "itemQuantity": 2
    },
    {
      "itemName": "Mouse",
      "itemQuantity": 5
    }
  ]
}
```

**Response (Success - HTTP 201):**
```json
{
  "orderId": "1",
  "status": "CREATED",
  "totalPrice": 2500.00,
  "message": "Order created successfully"
}
```

**Response (Error - HTTP 5xx):**
```json
{
  "orderId": null,
  "status": null,
  "totalPrice": null,
  "message": "Customer validation failed or Item validation failed"
}
```

---

### 2. Business Logic Flow

The `OrderManagementService` implements the following workflow:

#### Step A: Customer Validation
- Calls the **Customer Service** via Feign client
- Validates that the customer exists
- Endpoint: `GET /customers/{customerId}`
- Configured URL: `${services.customer-service.url}` (default: `http://localhost:8081`)

#### Step B: Item Validation and Pricing
- Calls the **Item Service** for each item via Feign client
- Validates item availability and retrieves pricing
- Endpoint: `GET /items/{itemName}`
- Configured URL: `${services.item-service.url}` (default: `http://localhost:8082`)
- Creates OrderItem objects with pricing information

#### Step C: Database Persistence
- Persists Order entity to the `order` table with generated ID
- Persists OrderLineItem entities to the `order_line_item` table
- Uses Spring Data JPA repositories for database operations

#### Step D: Event Publishing
- Publishes `OrderCreatedEvent` to Kafka topic `OrderCreated`
- Event contains order details, customer ID, items, and total price

#### Step E: Invoice Generation
- Invokes InvoiceService to generate invoice
- Returns invoice ID for reference

---

### 3. Database Schema

#### Table: `order`
| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | BIGINT | NO | Primary Key, Auto-generated |
| cust_id | BIGINT | NO | Customer ID (Foreign Key) |
| order_desc | VARCHAR | YES | Order description |
| order_date | DATE | NO | Order date |
| total_price | DOUBLE | YES | Total price of the order |
| created_at | TIMESTAMP | YES | Creation timestamp |
| status | VARCHAR | YES | Order Status enum |

#### Table: `order_line_item`
| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | BIGINT | NO | Primary Key, Auto-generated |
| item_name | VARCHAR | NO | Item name |
| item_quantity | INT | NO | Quantity ordered |
| order_id | BIGINT | NO | Foreign Key to order table |

---

### 4. Event-Driven Architecture

#### Kafka Configuration
- **Bootstrap Servers:** `${spring.kafka.bootstrap-servers}` (default: `localhost:9092`)
- **Topic:** `OrderCreated`
- **Key Serializer:** StringSerializer
- **Value Serializer:** JsonSerializer

#### OrderCreatedEvent Structure
```json
{
  "eventId": "uuid-string",
  "eventType": "OrderCreated",
  "timestamp": "2026-07-25T13:46:14.123Z",
  "data": {
    "orderId": "1",
    "customerId": 123,
    "orderDate": "2026-07-25",
    "totalPrice": 2500.00,
    "items": [
      {
        "itemName": "Laptop",
        "quantity": 2,
        "price": 1000.00
      },
      {
        "itemName": "Mouse",
        "quantity": 5,
        "price": 50.00
      }
    ]
  }
}
```

---

### 5. Project Structure

```
src/main/java/com/order/service/
├── OrderServiceApplication.java (with @EnableFeignClients)
├── util/
│   └── OrderStatus.java
├── controller/
│   └── OrderHandler.java (new endpoint)
├── dto/
│   ├── CreateOrder.java
│   ├── OrderItem.java (modified)
│   └── OrderResponse.java
├── entity/
│   ├── Order.java (existing DTO entity)
│   ├── OrderEntity.java (JPA entity - NEW)
│   └── OrderLineItem.java (JPA entity - NEW)
├── repository/
│   ├── OrderRepository.java (NEW)
│   └── OrderLineItemRepository.java (NEW)
├── client/
│   ├── CustomerServiceClient.java (Feign - NEW)
│   ├── CustomerResponse.java (NEW)
│   ├── ItemServiceClient.java (Feign - NEW)
│   └── ItemResponse.java (NEW)
├── config/
│   └── KafkaConfig.java (NEW)
├── events/
│   ├── OrderCreatedEvent.java
│   ├── OrderEventData.java
│   └── publisher/
│       └── EventPublisher.java (updated)
└── service/
    ├── Invoice.java (updated)
    └── impl/
        ├── OrderManagementService.java (updated)
        ├── InvoiceService.java (updated)
        └── DefaultPricingStrategy.java
```

---

### 6. Dependencies Added

The following dependencies were added to `pom.xml`:

```xml
<!-- JPA for database persistence -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- H2 In-memory database for development -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- JPA Test support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
```

---


```properties
# Database Configuration
spring.datasource.url=jdbc:h2:mem:orderdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 Console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# JPA/Hibernate
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3

# External Services
services.customer-service.url=http://localhost:8081
services.item-service.url=http://localhost:8082
```

---

### 8. Service Interfaces

#### CustomerServiceClient (Feign)
```java
@FeignClient(name = "customer-service", url = "${services.customer-service.url:http://localhost:8081}")
public interface CustomerServiceClient {
    @GetMapping("/customers/{customerId}")
    CustomerResponse getCustomer(@PathVariable Long customerId);
}
```

#### ItemServiceClient (Feign)
```java
@FeignClient(name = "item-service", url = "${services.item-service.url:http://localhost:8081}")
public interface ItemServiceClient {
    @GetMapping("/items/{itemName}")
    ItemResponse getItem(@PathVariable String itemName);
}
```

---

### 9. Error Handling

The service includes comprehensive error handling:

- **Customer Validation Failure:** Throws RuntimeException with message "Customer validation failed"
- **Item Validation Failure:** Throws RuntimeException with message "Item validation failed"
- **Database errors:** Handled by Spring's transaction rollback with @Transactional
- **Kafka publishing errors:** Logged but don't fail the entire request

---

### 10. Logging

Comprehensive logging is configured:

```properties
logging.level.com.order.service=INFO
logging.level.org.springframework.web=DEBUG
```

Key log messages:
- Order creation initiation
- Customer validation status
- Item validation status per item
- Order persistence confirmation with ID
- Line item persistence confirmation
- Kafka event publishing confirmation

---

## Testing

### Prerequisites
1. Ensure Customer Service is running on `http://localhost:8081` with endpoint `GET /customers/{id}`
2. Ensure Item Service is running on `http://localhost:8082` with endpoint `GET /items/{itemName}`
3. Ensure Kafka is running on `localhost:9092`

### Sample cURL Request

```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "orderDesc": "Office Supplies Order",
    "orderDate": "2026-07-25",
    "items": [
      {
        "itemName": "Pen",
        "itemQuantity": 100
      },
      {
        "itemName": "Notebook",
        "itemQuantity": 50
      }
    ]
  }'
```

### Sample Response

```json
{
  "orderId": "1",
  "status": "CREATED",
  "totalPrice": 1500.00,
  "message": "Order created successfully"
}
```

---

## Running the Application

```bash
# Build the project
./mvnw.cmd clean package

# Run the application
./mvnw.cmd spring-boot:run

# Access H2 Console (for development)
# http://localhost:8080/h2-console
```

---

## Key Features

✅ **RESTful API** with proper HTTP status codes  
✅ **Multi-service validation** using Feign clients  
✅ **Database persistence** with JPA and H2  
✅ **Event-driven architecture** with Kafka  
✅ **Transactional integrity** with @Transactional  
✅ **Comprehensive logging** for debugging  
✅ **Clean code patterns** using Lombok and dependency injection  
✅ **Error handling** with meaningful error messages  
✅ **Spring Boot best practices** throughout  

---

## Future Enhancements

1. Add authentication and authorization (Spring Security)
2. Implement circuit breaker for service calls (Resilience4j)
3. Add API documentation (Swagger/Springdoc)
4. Implement caching for item prices
5. Add order retrieval endpoint
6. Implement order status tracking
7. Add payment processing integration
8. Migrate from H2 to production database (PostgreSQL/MySQL)

---

## Troubleshooting

### Issue: "Customer validation failed"
- Check if Customer Service is running on port 8082
- Verify the endpoint exists: `GET /customers/{customerId}`
- Check the customerId in the request

### Issue: "Item validation failed"
- Check if Item Service is running on port 8081
- Verify the endpoint exists: `GET /items/{itemName}`
- Check the item names in the request

### Issue: Kafka event not published
- Ensure Kafka broker is running on `localhost:9092`
- Check Kafka logs for any errors
- Verify topics are created (OrderCreated topic)

### Issue: Database errors
- Check PostgreSQL/H2 logs
- Verify connection string in application.yml
- Ensure tables are created (handled by Hibernate with ddl-auto=create-drop)

---

## Contact

For issues or questions, please contact the development team.

