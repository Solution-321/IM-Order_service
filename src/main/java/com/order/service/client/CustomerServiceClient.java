package com.order.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", url = "${services.customer-service.url}")
public interface CustomerServiceClient {

    @GetMapping("/customer/{id}")
    CustomerResponse getCustomer(@PathVariable Long id);
}

