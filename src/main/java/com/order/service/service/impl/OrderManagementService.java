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
import com.order.service.service.PricingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderManagementService {

    private final OrderRepository orderRepository;
    private final PricingStrategy pricingStrategy;
    private final InvoiceService invoiceService;
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

        // Calculate total price
        double totalPrice = pricingStrategy.calculateTotal(validatedItems);

        // Step c: Create and persist OrderEntity
        OrderEntity order = OrderEntity.builder()
                .customerId(request.getCustomerId())
                .orderDesc(request.getOrderDesc())
                .orderDate(request.getOrderDate())
                .totalPrice(totalPrice)
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .lineItems(new ArrayList<>())
                .build();

        // Save order first to get the ID
        OrderEntity savedOrder = orderRepository.save(order);
        log.info("Order persisted with ID: {}", savedOrder.getId());

        // Create and persist line items
        final OrderEntity orderToReference = savedOrder; // Make final for lambda use
        List<OrderLineItem> lineItems = validatedItems.stream()
                .map(item -> OrderLineItem.builder()
                        .itemName(item.getItemName())
                        .itemQuantity(item.getQuantity())
                        .order(orderToReference)
                        .build())
                .toList();

        savedOrder.setLineItems(lineItems);
        savedOrder = orderRepository.save(savedOrder);
        log.info("Line items persisted for Order ID: {}", savedOrder.getId());

        // Generate Invoice
        String invoiceId = invoiceService.generateInvoice(savedOrder);
        log.info("Invoice generated: {} for Order ID: {}", invoiceId, savedOrder.getId());

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
            var customerResponse = customerServiceClient.getCustomer(customerId);
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


