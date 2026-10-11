package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderPurchaseVerifierTest {

    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final OrderPurchaseVerifier verifier = new OrderPurchaseVerifier(orderItemRepository);

    @ParameterizedTest(name = "purchased={0}")
    @CsvSource({"true", "false"})
    void looksUpPurchaseByParsedCustomerName(boolean purchased) {
        when(orderItemRepository.existsPurchase(7L, "Anna", "Maria Sidorova")).thenReturn(purchased);

        assertThat(verifier.hasPurchased(" Anna Maria Sidorova ", 7L)).isEqualTo(purchased);
    }

    @ParameterizedTest(name = "name=[{0}]")
    @CsvSource(nullValues = "null", value = {"null", "''", "'  '"})
    void customerWithoutNameHasNoPurchases(String customerFullName) {
        assertThat(verifier.hasPurchased(customerFullName, 7L)).isFalse();
        verifyNoInteractions(orderItemRepository);
    }
}
