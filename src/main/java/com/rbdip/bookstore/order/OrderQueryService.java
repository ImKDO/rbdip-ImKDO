package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Чтение заказов отделено от сценария их создания (OrderService). */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderView> listOrders() {
        return orderRepository.findAllWithCustomerAndItems().stream().map(OrderView::from).toList();
    }
}
