package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerName;
import com.rbdip.bookstore.product.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderQueryServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderQueryService service = new OrderQueryService(orderRepository);

    @Test
    void mapsOrdersWithCustomerNameAndItems() {
        Order anna = order(1L, new CustomerName("Anna", "Sidorova"));
        anna.addItem(new Product("Refactoring", new BigDecimal("40.00"), null), 1);
        anna.addItem(new Product("Clean Code", new BigDecimal("35.00"), null), 3);
        Order madonna = order(2L, new CustomerName("Madonna", ""));
        when(orderRepository.findAllWithCustomerAndItems()).thenReturn(List.of(anna, madonna));

        assertThat(service.listOrders()).containsExactly(
                new OrderView(1L, "Anna Sidorova", "new", List.of(
                        new OrderView.ItemView("Refactoring", 1),
                        new OrderView.ItemView("Clean Code", 3))),
                new OrderView(2L, "Madonna", "new", List.of()));
    }

    private static Order order(long id, CustomerName customerName) {
        Order order = new Order(new Customer(customerName, "SPb", null), Order.STATUS_NEW);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
