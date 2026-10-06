package com.example.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty List<@Valid CreateOrderItemRequest> items
) {
    public record CreateOrderItemRequest(
            @NotBlank String productId,
            @Positive int quantity
    ) {}
}
