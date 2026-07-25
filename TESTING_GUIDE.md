# Order Service API - Testing Guide

## Quick Start Testing

This guide provides step-by-step instructions for testing the Order Service REST API.

---

## Prerequisites

Before testing, ensure the following services are running:

1. **Order Service** (this service)
   - Port: 8080
   - Start command: `./mvnw.cmd spring-boot:run`

2. **Customer Service**
    - Port: 8082
    - Must provide endpoint: `GET /customers/{customerId}`
    - Mock Response:
      ```json
      {
        "customerId": 1,
        "customerName": "John Doe",
        "email": "john@example.com"
      }
      ```

3. **Item Service**
    - Port: 8081
    - Must provide endpoint: `GET /items/{itemName}`
    - Mock Response:
      ```json
      {
        "itemName": "Laptop",
        "price": 1000.00,
        "availableQuantity": 100
      }
      ```

4. **Kafka Server**
   - Port: 9092
   - Topic: `OrderCreated`

---

## Test Case 1: Successful Order Creation

**Objective:** Create a complete order with multiple items

### Request
```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "orderDesc": "Electronics Purchase",
    "orderDate": "2026-07-25",
    "items": [
      {
        "itemName": "Laptop",
        "itemQuantity": 1
      },
      {
        "itemName": "Mouse",
        "itemQuantity": 2
      }
    ]
  }'
```

### Expected Response (HTTP 201 Created)
```json
{
  "orderId": "1",
  "status": "CREATED",
  "totalPrice": 1100.00,
  "message": "Order created successfully"
}
```

### Validation Steps
1. ✅ Response status code should be **201 Created**
2. ✅ OrderId should be returned (typically "1" for first order)
3. ✅ Status should be "CREATED"
4. ✅ Total price should be calculated based on item prices
5. ✅ Message should confirm successful creation

### Backend Verification
1. Check logs for confirmation messages:
   ```
   Creating order via /service/orders endpoint for customer: 1
   Validating customer with ID: 1
   Customer validation successful for ID: 1
   Validating 2 items
   Validating item: Laptop
   Item validation successful: Laptop, Price: 1000.0
   Validating item: Mouse
   Item validation successful: Mouse, Price: 50.0
   Order persisted with ID: 1
   Line items persisted for Order ID: 1
   Invoice generated: Invoice-PDF-1 for Order ID: 1
   Order Created Event published successfully for Order ID: 1
   ```

2. Check H2 Database:
   - URL: `http://localhost:8080/h2-console`
   - Credentials: username `sa`, password (empty)
   - Query table `order`: `SELECT * FROM "order"`
   - Query table `order_line_item`: `SELECT * FROM order_line_item`

3. Verify Kafka Event:
   - Use a Kafka consumer to check the `OrderCreated` topic
   - Event body should contain order details

---

## Test Case 2: Invalid Customer

**Objective:** Test customer validation failure

### Request
```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 99999,
    "orderDesc": "Test Order",
    "orderDate": "2026-07-25",
    "items": [
      {
        "itemName": "Laptop",
        "itemQuantity": 1
      }
    ]
  }'
```

### Expected Response (HTTP 500 Internal Server Error)
```json
{
  "timestamp": "2026-07-25T13:46:14.123Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Customer validation failed: 404 Not Found"
}
```

### Validation Steps
1. ✅ Response status code should be **500** (or appropriate error code)
2. ✅ Error message should indicate customer validation failure
3. ✅ No order should be created in the database
4. ✅ No Kafka event should be published

---

## Test Case 3: Invalid Item

**Objective:** Test item validation failure

### Request
```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "orderDesc": "Test Order",
    "orderDate": "2026-07-25",
    "items": [
      {
        "itemName": "NonExistentItem",
        "itemQuantity": 1
      }
    ]
  }'
```

### Expected Response (HTTP 500 Internal Server Error)
```json
{
  "timestamp": "2026-07-25T13:46:14.123Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Item validation failed: 404 Not Found"
}
```

### Validation Steps
1. ✅ Response status code should be **500** (or appropriate error code)
2. ✅ Error message should indicate item validation failure
3. ✅ Order transaction should be rolled back
4. ✅ No data should persist in database

---

## Test Case 4: Missing Required Fields

**Objective:** Test validation of required fields

### Request (Missing customerId)
```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderDesc": "Test Order",
    "orderDate": "2026-07-25",
    "items": [
      {
        "itemName": "Laptop",
        "itemQuantity": 1
      }
    ]
  }'
```

### Expected Response (HTTP 400 Bad Request)
```json
{
  "timestamp": "2026-07-25T13:46:14.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "customerId",
      "message": "must not be null"
    }
  ]
}
```

### Validation Steps
1. ✅ Response status code should be **400 Bad Request**
2. ✅ Error message should indicate validation failure
3. ✅ Specific field error should be mentioned
4. ✅ Order creation should not proceed

---

## Test Case 5: Empty Items List

**Objective:** Test validation of items list

### Request
```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "orderDesc": "Test Order",
    "orderDate": "2026-07-25",
    "items": []
  }'
```

### Expected Response (HTTP 400 Bad Request)
```json
{
  "timestamp": "2026-07-25T13:46:14.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "items",
      "message": "must not be empty"
    }
  ]
}
```

### Validation Steps
1. ✅ Response status code should be **400 Bad Request**
2. ✅ Error message should indicate empty items list
3. ✅ Order creation should not proceed

---

## Test Case 6: Multiple Orders

**Objective:** Test creating multiple orders sequentially

### Request 1
```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "orderDesc": "First Order",
    "orderDate": "2026-07-25",
    "items": [
      {
        "itemName": "Laptop",
        "itemQuantity": 1
      }
    ]
  }'
```

### Response 1 (HTTP 201 Created)
```json
{
  "orderId": "1",
  "status": "CREATED",
  "totalPrice": 1000.00,
  "message": "Order created successfully"
}
```

### Request 2
```bash
curl -X POST http://localhost:8080/service/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "orderDesc": "Second Order",
    "orderDate": "2026-07-25",
    "items": [
      {
        "itemName": "Mouse",
        "itemQuantity": 3
      }
    ]
  }'
```

### Response 2 (HTTP 201 Created)
```json
{
  "orderId": "2",
  "status": "CREATED",
  "totalPrice": 150.00,
  "message": "Order created successfully"
}
```

### Validation Steps
1. ✅ Each order should get a unique ID (1, 2, 3, ...)
2. ✅ Both orders should be persisted independently
3. ✅ Each order should have its own line items
4. ✅ Database should show 2 separate order records

---

## Database Verification Guide

### Connect to H2 Console
1. Open browser: `http://localhost:8080/h2-console`
2. Connection settings:
   - Driver Class: `org.h2.Driver`
   - JDBC URL: `jdbc:h2:mem:orderdb`
   - Username: `sa`
   - Password: (leave empty)
3. Click "Connect"

### Verify Order Table
```sql
SELECT * FROM "order";
```
Should show columns: `id`, `cust_id`, `order_desc`, `order_date`, `total_price`, `created_at`, `status`

### Verify Order Line Item Table
```sql
SELECT * FROM order_line_item;
```
Should show columns: `id`, `item_name`, `item_quantity`, `order_id`

### Query Specific Order
```sql
SELECT o.id, o.cust_id, o.order_desc, o.order_date, o.total_price, 
       li.id as line_id, li.item_name, li.item_quantity
FROM "order" o
LEFT JOIN order_line_item li ON o.id = li.order_id
WHERE o.id = 1;
```

---

## Kafka Event Verification

### Using Kafka Console Consumer

```bash
# Start a Kafka consumer for the OrderCreated topic
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic OrderCreated --from-beginning
```

### Expected Event Output
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "OrderCreated",
  "timestamp": "2026-07-25T13:46:14.123Z",
  "data": {
    "orderId": "1",
    "customerId": 1,
    "orderDate": "2026-07-25",
    "totalPrice": 1000.00,
    "items": [
      {
        "itemName": "Laptop",
        "quantity": 1,
        "price": 1000.00
      }
    ]
  }
}
```

---

## Performance Testing

### Load Test Script

```bash
#!/bin/bash

# Send 10 concurrent requests
for i in {1..10}; do
  curl -X POST http://localhost:8080/service/orders \
    -H "Content-Type: application/json" \
    -d '{
      "customerId": '$i',
      "orderDesc": "Stress Test Order '$i'",
      "orderDate": "2026-07-25",
      "items": [
        {
          "itemName": "Laptop",
          "itemQuantity": 1
        }
      ]
    }' &
done

wait
echo "All requests completed"
```

### Response Time Expectations
- Successful creation: **100-500ms**
- Database persistence: **< 100ms**
- Kafka publishing: **< 50ms**
- Total: **< 1000ms**

---

## Debugging Tips

### Enable Debug Logging
Add to `application.yml`:
```yaml
logging:
  level:
    com.order.service: DEBUG
    org.springframework.web: TRACE
```

### Check Application Logs
```bash
# Spring Boot logs are printed to console
# Look for INFO messages prefixed with: "REST API:" or "Creating order"
```

### Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| Customer validation fails | Ensure Customer Service is running on port 8081 with correct endpoint |
| Item validation fails | Ensure Item Service is running on port 8082 with expected response format |
| Kafka event not sent | Check Kafka is running and topic exists; check broker.log |
| Database constraint error | Ensure H2 is running with correct schema |
| HTTP 404 on endpoint | Verify URL is exactly `/service/orders` (note: not `/orders/service`) |

---

## Test Report Template

```
Date: [Date]
Tester: [Name]
Environment: [Dev/Test/Prod]

Test Results:
- Test Case 1 (Success): [PASS/FAIL]
- Test Case 2 (Invalid Customer): [PASS/FAIL]
- Test Case 3 (Invalid Item): [PASS/FAIL]
- Test Case 4 (Missing Fields): [PASS/FAIL]
- Test Case 5 (Empty Items): [PASS/FAIL]
- Test Case 6 (Multiple Orders): [PASS/FAIL]

Database Verification: [PASSED/FAILED]
Kafka Verification: [PASSED/FAILED]

Issues Found:
1. [Description and step to reproduce]

Performance Metrics:
- Avg Response Time: [Time]ms
- Min Response Time: [Time]ms
- Max Response Time: [Time]ms
- Total Successful Requests: [Number]
- Total Failed Requests: [Number]
```

---

## Automation with Postman

### Postman Collection (JSON)

```json
{
  "info": {
    "name": "Order Service API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Create Order - Success",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\"customerId\": 1, \"orderDesc\": \"Electronics\", \"orderDate\": \"2026-07-25\", \"items\": [{\"itemName\": \"Laptop\", \"itemQuantity\": 1}]}"
        },
        "url": {
          "raw": "http://localhost:8080/service/orders",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["service", "orders"]
        }
      }
    }
  ]
}
```

---

## Next Steps

1. Deploy services in order: Customer Service → Item Service → Kafka → Order Service
2. Run Test Case 1 to verify basic functionality
3. Run remaining test cases to ensure robustness
4. Check database and Kafka integration
5. Perform load testing to establish baseline performance
6. Document any deviations from expected behavior


