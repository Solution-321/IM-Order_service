package com.order.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "item-service", url = "${services.item-service.url}")
public interface ItemServiceClient {

    @GetMapping("/items/{itemName}")
    ItemResponse getItem(@PathVariable String itemName);
}

