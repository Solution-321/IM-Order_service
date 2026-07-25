package com.order.service.events.publisher;

import com.order.service.entity.OrderEntity;
import com.order.service.events.avro.OrderCreatedEvent;
import com.order.service.events.avro.OrderEventData;
import com.order.service.events.avro.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventPublisher {

    public static final String ORDERS_TOPIC = "OrderCreated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderEntity order) {
        log.info("Publishing Order Created Event for Order ID: {}", order.getId());
        // Build Avro OrderEventData
        OrderEventData.Builder dataBuilder = OrderEventData.newBuilder()
                .setOrderId(order.getId().toString())
                .setCustomerId(order.getCustomerId())
                .setOrderDate(order.getOrderDate() == null ? null : order.getOrderDate().toString())
                .setTotalPrice(order.getTotalPrice());

        // Build Avro items array
        List<OrderItem> items = order.getLineItems().stream()
                .map(li -> {
                    return OrderItem.newBuilder()
                            .setItemName(li.getItemName())
                            .setProductId(li.getItemName())
                            .setPrice(0.0)
                            .setQuantity(li.getItemQuantity() == null ? 0 : li.getItemQuantity())
                            .build();
                })
                .toList();

        dataBuilder.setItems(items);

        OrderCreatedEvent avroEvent = OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType("OrderCreated")
                .setTimestamp(Instant.now().toEpochMilli())
                .setData(dataBuilder.build())
                .build();

        kafkaTemplate.send(ORDERS_TOPIC, avroEvent.getEventId().toString(), avroEvent);
        log.info("Order Created Event published successfully for Order ID: {}", order.getId());
    }
}
