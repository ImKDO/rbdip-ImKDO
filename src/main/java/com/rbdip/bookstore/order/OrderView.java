package com.rbdip.bookstore.order;

import java.util.List;

/** Представление заказа в ответе GET /orders. */
public record OrderView(Long id, String customerFullName, String status, List<ItemView> items) {

    public record ItemView(String productName, Integer quantity) {
    }

    static OrderView from(Order order) {
        return new OrderView(
                order.getId(),
                order.getCustomerFullName(),
                order.getStatus(),
                order.getItems().stream()
                        .map(item -> new ItemView(item.getProductName(), item.getQuantity()))
                        .toList());
    }
}
