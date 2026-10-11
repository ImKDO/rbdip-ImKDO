package com.rbdip.bookstore.order;

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
 * (PricingCalculator), сохранение (репозитории), уведомление клиента
 * (OrderPlacedEvent -> OrderConfirmationSender после коммита).
 */
@Service
public class OrderService {

    private final OrderRequestValidator validator;
    private final PricingCalculator pricingCalculator;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            OrderRequestValidator validator,
            PricingCalculator pricingCalculator,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ApplicationEventPublisher eventPublisher) {
        this.validator = validator;
        this.pricingCalculator = pricingCalculator;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
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

        Order order = orderRepository.save(new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), Order.STATUS_NEW));
        orderItemRepository.saveAll(lines.stream().map(line -> line.toOrderItem(order.getId())).toList());

        eventPublisher.publishEvent(new OrderPlacedEvent(order.getId(), request.customerFullName(), total));
        return order;
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

        OrderItem toOrderItem(Long orderId) {
            return new OrderItem(orderId, product.getName(), product.getPrice(), quantity);
        }
    }
}
