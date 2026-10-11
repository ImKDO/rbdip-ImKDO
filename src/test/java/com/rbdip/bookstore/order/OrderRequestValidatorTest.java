package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderRequestValidatorTest {

    private final OrderRequestValidator validator = new OrderRequestValidator();

    private static CreateOrderRequest request(String name, String address, List<CreateOrderRequest.Item> items) {
        return new CreateOrderRequest(name, address, null, null, null, items);
    }

    private static List<CreateOrderRequest.Item> oneItem(Long productId, Integer quantity) {
        List<CreateOrderRequest.Item> items = new ArrayList<>();
        items.add(new CreateOrderRequest.Item(productId, quantity));
        return items;
    }

    @Test
    void acceptsCompleteRequest() {
        assertThatCode(() -> validator.validate(request("Ivan Petrov", "Moscow", oneItem(1L, 2))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsItemWithoutQuantityBecauseItDefaultsToOne() {
        assertThatCode(() -> validator.validate(request("Ivan Petrov", "Moscow", oneItem(1L, null))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "name=[{0}]")
    @CsvSource(nullValues = "null", value = {"null", "''", "'   '"})
    void requiresCustomerName(String name) {
        assertThatThrownBy(() -> validator.validate(request(name, "Moscow", oneItem(1L, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customerFullName is required");
    }

    @ParameterizedTest(name = "address=[{0}]")
    @CsvSource(nullValues = "null", value = {"null", "''", "'   '"})
    void requiresCustomerAddress(String address) {
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", address, oneItem(1L, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customerAddress is required");
    }

    @Test
    void requiresAtLeastOneItem() {
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", null)))
                .hasMessage("order must contain at least one item");
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", List.of())))
                .hasMessage("order must contain at least one item");
    }

    @Test
    void requiresProductOfEveryItem() {
        List<CreateOrderRequest.Item> withNullItem = oneItem(1L, 1);
        withNullItem.add(null);

        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", withNullItem)))
                .hasMessage("productId is required");
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", oneItem(null, 1))))
                .hasMessage("productId is required");
    }

    @ParameterizedTest(name = "quantity={0}")
    @CsvSource({"0", "-1"})
    void requiresPositiveQuantity(int quantity) {
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", oneItem(1L, quantity))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive");
    }
}
