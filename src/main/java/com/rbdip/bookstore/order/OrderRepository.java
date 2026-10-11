package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Один SQL-запрос вместо 1 + N: клиент, позиции и товары позиций
    // загружаются вместе с заказами через JOIN FETCH.
    @Query("""
            select o from Order o
            join fetch o.customer
            left join fetch o.items i
            left join fetch i.product
            order by o.id""")
    List<Order> findAllWithCustomerAndItems();
}
