package com.order.service.controller;

import com.order.service.dto.CreateOrder;
import com.order.service.dto.OrderResponse;
import com.order.service.service.impl.OrderManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
@Slf4j
public class OrderHandler {

    private final OrderManagementService orderManagementService;

    // Keep only the public API required by the spec: POST /service/orders
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid CreateOrder request) {
        log.info("Creating order for customer: {}", request.getCustomerId());
        var response = orderManagementService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}


