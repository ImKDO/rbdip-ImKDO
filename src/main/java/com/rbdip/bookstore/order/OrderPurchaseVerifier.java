package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.CustomerName;
import com.rbdip.bookstore.review.PurchaseVerifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реализация контракта модуля отзывов на стороне модуля заказов: review
 * знает только интерфейс PurchaseVerifier, а не репозитории и сущности order.
 */
@Component
public class OrderPurchaseVerifier implements PurchaseVerifier {

    private final OrderItemRepository orderItemRepository;

    public OrderPurchaseVerifier(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchased(String customerFullName, Long productId) {
        if (customerFullName == null || customerFullName.isBlank()) {
            return false;
        }
        CustomerName name = CustomerName.parse(customerFullName);
        return orderItemRepository.existsPurchase(productId, name.firstName(), name.lastName());
    }
}
