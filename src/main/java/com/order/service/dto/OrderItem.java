package com.order.service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItem {

    private String itemName;
    private Double price;
    private String productId;
    private Integer quantity;
    private Integer itemQuantity;
}
