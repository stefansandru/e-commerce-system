package com.commerce.order.interfaces;

import com.commerce.order.application.OrderCommandService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderCommandService orderCommandService;

    public OrderController(OrderCommandService orderCommandService) {
        this.orderCommandService = orderCommandService;
    }

    @PostMapping("/checkout")
    @RateLimiter(name = "checkoutLimiter", fallbackMethod = "checkoutFallback")
    public ResponseEntity<CheckoutResponse> checkout(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CheckoutRequest request) {
        UUID orderId = orderCommandService.checkout(request.productId(), request.quantity(), idempotencyKey);
        return ResponseEntity.ok(new CheckoutResponse(orderId.toString()));
    }

    @SuppressWarnings("UnusedDeclaration")
    public ResponseEntity<CheckoutResponse> checkoutFallback(String idempotencyKey, CheckoutRequest request,
            Throwable t) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    public record CheckoutRequest(String productId, int quantity) {
    }

    public record CheckoutResponse(String orderId) {
    }
}
