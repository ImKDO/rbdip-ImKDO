package com.rbdip.bookstore.order;

import org.springframework.stereotype.Component;

/**
 * Проверка входного запроса на создание заказа. Нарушение - это
 * IllegalArgumentException, которое GlobalExceptionHandler превращает в 400.
 */
@Component
public class OrderRequestValidator {

    public void validate(CreateOrderRequest request) {
        requireText(request.customerFullName(), "customerFullName is required");
        requireText(request.customerAddress(), "customerAddress is required");
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
        request.items().forEach(OrderRequestValidator::validateItem);
    }

    private static void validateItem(CreateOrderRequest.Item item) {
        if (item == null || item.productId() == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (item.quantityOrDefault() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
