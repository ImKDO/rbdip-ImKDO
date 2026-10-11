package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CustomerTypeTest {

    @ParameterizedTest
    @EnumSource(CustomerType.class)
    void isFoundByItsOwnCode(CustomerType type) {
        assertThat(CustomerType.fromCode(type.getCode())).contains(type);
    }

    @ParameterizedTest(name = "code=[{0}]")
    @CsvSource(nullValues = "null", value = {"null", "VIP", "gold"})
    void unknownCodeMeansNoType(String code) {
        assertThat(CustomerType.fromCode(code)).isEmpty();
    }

    @Test
    void codesAreLowercaseNames() {
        assertThat(CustomerType.REGULAR.getCode()).isEqualTo("regular");
        assertThat(CustomerType.VIP.getCode()).isEqualTo("vip");
        assertThat(CustomerType.WHOLESALE.getCode()).isEqualTo("wholesale");
    }

    @Test
    void requestDefaultsToRegularCustomerAndSingleUnit() {
        assertThat(new CreateOrderRequest(null, null, null, null, null, null).customerTypeOrDefault())
                .isEqualTo("regular");
        assertThat(new CreateOrderRequest(null, null, null, "vip", null, null).customerTypeOrDefault())
                .isEqualTo("vip");
        assertThat(new CreateOrderRequest.Item(1L, null).quantityOrDefault()).isEqualTo(1);
        assertThat(new CreateOrderRequest.Item(1L, 5).quantityOrDefault()).isEqualTo(5);
    }
}
