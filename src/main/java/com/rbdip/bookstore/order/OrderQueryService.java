package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.domain.Sort;
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
        // N+1: клиент и позиции каждого заказа догружаются отдельными запросами. Цель ЛР4.
        return orderRepository.findAll(Sort.by("id")).stream().map(OrderView::from).toList();
    }
}
