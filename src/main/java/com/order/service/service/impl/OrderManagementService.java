package com.order.service.service.impl;

import com.order.service.OrderStatus;
import com.order.service.client.CustomerServiceClient;
import com.order.service.client.ItemServiceClient;
import com.order.service.dto.CreateOrder;
import com.order.service.dto.OrderItem;
import com.order.service.dto.OrderResponse;
import com.order.service.entity.OrderEntity;
import com.order.service.entity.OrderLineItem;
import com.order.service.events.publisher.EventPublisher;
import com.order.service.repository.OrderRepository;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderManagementService {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final CustomerServiceClient customerServiceClient;
    private final ItemServiceClient itemServiceClient;

    @Transactional
    public OrderResponse createOrder(CreateOrder request) {
        log.info("Creating order for customer: {}, description: {}",
                request.getCustomerId(), request.getOrderDesc());

        // Step a: Validate customer
        validateCustomer(request.getCustomerId());

        // Step b: Validate items and build order items with pricing
        List<OrderItem> validatedItems = validateAndPriceItems(request.getItems());

        // Calculate total price (sum price * quantity)
        double totalPrice = validatedItems.stream()
                .mapToDouble(i -> (i.getPrice() == null ? 0.0 : i.getPrice()) * (i.getQuantity() == null ? 0 : i.getQuantity()))
                .sum();

        // Step c: Create OrderEntity with its line items and persist in one operation
        OrderEntity order = OrderEntity.builder()
                .customerId(request.getCustomerId())
                .orderDesc(request.getOrderDesc())
                .orderDate(request.getOrderDate())
                .totalPrice(totalPrice)
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();

        List<OrderLineItem> lineItems = validatedItems.stream()
                .map(item -> OrderLineItem.builder()
                        .itemName(item.getItemName())
                        .itemQuantity(item.getQuantity())
                        .order(order)
                        .build())
                .collect(Collectors.toList());

        order.setLineItems(lineItems);

        OrderEntity savedOrder = orderRepository.save(order);
        log.info("Order persisted with ID: {} and {} line items", savedOrder.getId(),
                savedOrder.getLineItems() == null ? 0 : savedOrder.getLineItems().size());


        // Publish Kafka event
        eventPublisher.publishOrderCreated(savedOrder);

        return OrderResponse.builder()
                .orderId(savedOrder.getId().toString())
                .totalPrice(totalPrice)
                .status(savedOrder.getStatus().name())
                .message("Order created successfully")
                .build();
    }

    private void validateCustomer(Long customerId) {
        log.info("Validating customer with ID: {}", customerId);
        try {
            // If the client returns null or throws, treat as validation failure
            if (customerServiceClient.getCustomer(customerId) == null) {
                throw new RuntimeException("Customer not found: " + customerId);
            }
            log.info("Customer validation successful for ID: {}", customerId);
        } catch (Exception e) {
            log.error("Customer validation failed for ID: {}", customerId, e);
            throw new RuntimeException("Customer validation failed: " + e.getMessage(), e);
        }
    }

    private List<OrderItem> validateAndPriceItems(List<OrderItem> items) {
        log.info("Validating {} items", items.size());
        return items.stream()
                .map(item -> {
                    try {
                        log.info("Validating item: {}", item.getItemName());
                        var itemResponse = itemServiceClient.getItem(item.getItemName());
                        log.info("Item validation successful: {}, Price: {}",
                                item.getItemName(), itemResponse.getPrice());

                        // Support both quantity and itemQuantity
                        Integer qty = item.getQuantity() != null ?
                                item.getQuantity() : item.getItemQuantity();

                        return OrderItem.builder()
                                .itemName(item.getItemName())
                                .quantity(qty)
                                .itemQuantity(qty)
                                .price(itemResponse.getPrice())
                                .productId(item.getProductId() != null ?
                                        item.getProductId() : item.getItemName())
                                .build();
                    } catch (Exception e) {
                        log.error("Item validation failed for: {}", item.getItemName(), e);
                        throw new RuntimeException("Item validation failed: " + e.getMessage(), e);
                    }
                })
                .toList();
    }
}


