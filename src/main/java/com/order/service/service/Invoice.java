package com.order.service.service;

import com.order.service.entity.OrderEntity;

public interface Invoice {
    String generateInvoice(OrderEntity order);
}
