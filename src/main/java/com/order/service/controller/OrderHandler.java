package com.order.service.controller;

import com.order.service.dto.CreateOrder;
import com.order.service.dto.OrderResponse;
import com.order.service.service.impl.OrderManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OrderHandler {

    private final OrderManagementService orderManagementService;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid CreateOrder request) {
        log.info("creating {}, order for: {}",request.getOrderDesc(),request.getCustomerId());
        return ResponseEntity.ok(orderManagementService.createOrder(request));
    }

    @PostMapping("/service/orders")
    public ResponseEntity<OrderResponse> createOrderService(
            @RequestBody @Valid CreateOrder request) {
        log.info("REST API: Creating order via /service/orders endpoint for customer: {}",
                request.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderManagementService.createOrder(request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        // future implementation
        return ResponseEntity.ok(null);
    }
}


