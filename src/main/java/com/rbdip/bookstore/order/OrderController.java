package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderService orderService, OrderQueryService orderQueryService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreatedResponse createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return new OrderCreatedResponse(order.getId(), order.getStatus());
    }

    @GetMapping("/orders")
    public List<OrderView> listOrders() {
        return orderQueryService.listOrders();
    }

    public record OrderCreatedResponse(Long id, String status) {
    }
}
