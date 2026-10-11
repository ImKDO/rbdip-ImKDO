package com.rbdip.bookstore.order;

import java.util.List;

public record CreateOrderRequest(
        String customerFullName,
        String customerAddress,
        String customerPhone,
        String customerType,
        String couponCode,
        List<Item> items) {

    static final String DEFAULT_CUSTOMER_TYPE = CustomerType.REGULAR.getCode();

    String customerTypeOrDefault() {
        return customerType == null ? DEFAULT_CUSTOMER_TYPE : customerType;
    }

    public record Item(Long productId, Integer quantity) {

        static final int DEFAULT_QUANTITY = 1;

        int quantityOrDefault() {
            return quantity == null ? DEFAULT_QUANTITY : quantity;
        }
    }
}
