package com.order.service.client;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemResponse {
    private String itemName;
    private Double price;
    private Integer availableQuantity;
}

