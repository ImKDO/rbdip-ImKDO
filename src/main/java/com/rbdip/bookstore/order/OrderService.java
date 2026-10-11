package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerRegistry;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сценарий оформления заказа. Сам ничего не проверяет и не считает -
 * только координирует: валидация (OrderRequestValidator), расчёт цены
 * (PricingCalculator), клиент (CustomerRegistry), сохранение заказа вместе
 * с позициями (OrderRepository), уведомление клиента
 * (OrderPlacedEvent -> OrderConfirmationSender после коммита).
 */
@Service
public class OrderService {

    private final OrderRequestValidator validator;
    private final PricingCalculator pricingCalculator;
    private final ProductRepository productRepository;
    private final CustomerRegistry customerRegistry;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            OrderRequestValidator validator,
            PricingCalculator pricingCalculator,
            ProductRepository productRepository,
            CustomerRegistry customerRegistry,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher) {
        this.validator = validator;
        this.pricingCalculator = pricingCalculator;
        this.productRepository = productRepository;
        this.customerRegistry = customerRegistry;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);
        List<OrderLine> lines = resolveLines(request.items());
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                lines.stream().map(OrderLine::toLineItem).toList(),
                request.customerTypeOrDefault(),
                request.couponCode());

        Customer customer = customerRegistry.findOrRegister(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = new Order(customer, Order.STATUS_NEW);
        lines.forEach(line -> order.addItem(line.product(), line.quantity()));
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderPlacedEvent(saved.getId(), customer.getFullName(), total));
        return saved;
    }

    private List<OrderLine> resolveLines(List<CreateOrderRequest.Item> items) {
        Map<Long, Product> productsById = productRepository
                .findAllById(items.stream().map(CreateOrderRequest.Item::productId).toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return items.stream()
                .map(item -> new OrderLine(requireProduct(productsById, item.productId()), item.quantityOrDefault()))
                .toList();
    }

    private static Product requireProduct(Map<Long, Product> productsById, Long productId) {
        Product product = productsById.get(productId);
        if (product == null) {
            throw new IllegalArgumentException("product " + productId + " not found");
        }
        return product;
    }

    /** Позиция заказа с уже найденным товаром. */
    record OrderLine(Product product, int quantity) {

        PricingCalculator.LineItem toLineItem() {
            return new PricingCalculator.LineItem(product.getPrice(), quantity);
        }
    }
}
