package com.order.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", url = "${services.customer-service.url}")
public interface CustomerServiceClient {

    @GetMapping("/customers/{customerId}")
    CustomerResponse getCustomer(@PathVariable Long customerId);
}

