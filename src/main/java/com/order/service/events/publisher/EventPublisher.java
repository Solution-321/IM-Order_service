package com.order.service.events.publisher;

import com.order.service.dto.OrderItem;
import com.order.service.entity.OrderEntity;
import com.order.service.events.OrderCreatedEvent;
import com.order.service.events.OrderEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventPublisher {

    public static final String ORDERS_TOPIC = "OrderCreated";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void publishOrderCreated(OrderEntity order) {
        log.info("Publishing Order Created Event for Order ID: {}", order.getId());

        OrderEventData eventData = OrderEventData.builder()
                .orderId(order.getId().toString())
                .customerId(order.getCustomerId())
                .orderDate(order.getOrderDate())
                .totalPrice(order.getTotalPrice())
                .items(order.getLineItems().stream()
                        .map(li -> OrderItem.builder()
                                .itemName(li.getItemName())
                                .quantity(li.getItemQuantity())
                                .build())
                        .toList())
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("OrderCreated")
                .timestamp(Instant.now())
                .data(eventData)
                .build();

        kafkaTemplate.send(ORDERS_TOPIC, event.getEventId(), event);
        log.info("Order Created Event published successfully for Order ID: {}", order.getId());
    }
}
