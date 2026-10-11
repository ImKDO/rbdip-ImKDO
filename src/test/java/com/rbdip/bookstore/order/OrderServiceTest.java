package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerName;
import com.rbdip.bookstore.customer.CustomerRegistry;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class OrderServiceTest {

    private static final long SAVED_ORDER_ID = 42L;

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CustomerRegistry customerRegistry = mock(CustomerRegistry.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final OrderService service = new OrderService(
            new OrderRequestValidator(),
            new PricingCalculator(),
            productRepository,
            customerRegistry,
            orderRepository,
            eventPublisher);

    private final Product cleanCode = product(1L, "Clean Code", "35.00");
    private final Product refactoring = product(2L, "Refactoring", "40.00");
    private final Customer ivan = new Customer(new CustomerName("Ivan", "Petrov"), "Moscow", null);

    @BeforeEach
    void setUp() {
        when(productRepository.findAllById(any())).thenReturn(List.of(cleanCode, refactoring));
        when(customerRegistry.findOrRegister("Ivan Petrov", "Moscow", null)).thenReturn(ivan);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", SAVED_ORDER_ID);
            return order;
        });
    }

    @Test
    void savesNewOrderOfCustomerWithAllItems() {
        Order order = service.createOrder(request("vip", null,
                new CreateOrderRequest.Item(1L, 2), new CreateOrderRequest.Item(2L, 1)));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(order).isSameAs(saved.getValue());
        assertThat(order.getId()).isEqualTo(SAVED_ORDER_ID);
        assertThat(order.getStatus()).isEqualTo("new");
        assertThat(order.getCustomerFullName()).isEqualTo("Ivan Petrov");
        assertThat(order.getItems())
                .extracting(OrderItem::getProductName, OrderItem::getQuantity)
                .containsExactly(tuple("Clean Code", 2), tuple("Refactoring", 1));
    }

    @Test
    void publishesOrderPlacedEventWithCalculatedTotal() {
        service.createOrder(request("vip", "SAVE10",
                new CreateOrderRequest.Item(1L, 2), new CreateOrderRequest.Item(2L, 1)));

        // (2 * 35 + 40) * 0.9 - 10 = 89.00
        verify(eventPublisher).publishEvent(
                new OrderPlacedEvent(SAVED_ORDER_ID, "Ivan Petrov", new BigDecimal("89.00")));
    }

    @Test
    void appliesDefaultCustomerTypeAndQuantity() {
        Order order = service.createOrder(request(null, null, new CreateOrderRequest.Item(2L, null)));

        assertThat(order.getItems()).extracting(OrderItem::getQuantity).containsExactly(1);
        verify(eventPublisher).publishEvent(
                new OrderPlacedEvent(SAVED_ORDER_ID, "Ivan Petrov", new BigDecimal("40.00")));
    }

    @Test
    void loadsAllProductsOfOrderAtOnce() {
        service.createOrder(request(null, null,
                new CreateOrderRequest.Item(1L, 1), new CreateOrderRequest.Item(2L, 1)));

        verify(productRepository).findAllById(List.of(1L, 2L));
    }

    @Test
    void rejectsUnknownProductWithoutSavingAnything() {
        assertThatThrownBy(() -> service.createOrder(request(null, null, new CreateOrderRequest.Item(99L, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("product 99 not found");

        verifyNoInteractions(customerRegistry, orderRepository, eventPublisher);
    }

    @Test
    void rejectsInvalidRequestBeforeTouchingStorage() {
        CreateOrderRequest withoutItems = new CreateOrderRequest(
                "Ivan Petrov", "Moscow", null, null, null, List.of());

        assertThatThrownBy(() -> service.createOrder(withoutItems)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(productRepository, customerRegistry, orderRepository, eventPublisher);
    }

    private static CreateOrderRequest request(String customerType, String coupon, CreateOrderRequest.Item... items) {
        return new CreateOrderRequest("Ivan Petrov", "Moscow", null, customerType, coupon, List.of(items));
    }

    private static Product product(long id, String name, String price) {
        Product product = new Product(name, new BigDecimal(price), null);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
