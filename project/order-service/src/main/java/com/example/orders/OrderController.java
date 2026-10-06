package com.example.orders;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderRepository orders;

    public OrderController(OrderRepository orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity()))
                .toList();
        Order saved = orders.save(new Order(request.customerId(), items));
        OrderResponse response = OrderResponse.from(saved);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    public record OrderResponse(Long id, String customerId, List<OrderItemResponse> items) {
        static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getCustomerId(),
                    order.getItems().stream()
                            .map(item -> new OrderItemResponse(item.getProductId(), item.getQuantity()))
                            .toList());
        }
    }

    public record OrderItemResponse(String productId, int quantity) {}
}
