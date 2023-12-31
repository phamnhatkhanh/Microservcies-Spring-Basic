package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.InventoryResponse;
import com.ecommerce.orderservice.dto.OrderLineItemsDto;
import com.ecommerce.orderservice.dto.OrderRequest;
import com.ecommerce.orderservice.model.OrderLineItems;
import com.ecommerce.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final WebClient.Builder webClientBuilder;
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> isInStock(@RequestBody OrderRequest orderRequest) {
        log.info("Start prepare {}");

        List<String> skuCodes = orderRequest.getOrderLineItemsDtoList().stream()
                .map(OrderLineItemsDto::getSkuCode)
                .toList();

        log.info("Convent param: {}", skuCodes);
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://localhost:8082/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        log.info("Convent result: {}", inventoryResponseArray[0].toString());
        return Arrays.stream(inventoryResponseArray).toList();
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<String> placeOrder(@RequestBody OrderRequest orderRequest) {
        log.info("Placing Order: {}",orderRequest.toString());

        return CompletableFuture.supplyAsync(() -> orderService.placeOrder(orderRequest));
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest, RuntimeException runtimeException) {
        log.info("Cannot Place Order Executing Fallback logic");
        return CompletableFuture.supplyAsync(() -> "Oops! Something went wrong, please order after some time!");
    }
}
