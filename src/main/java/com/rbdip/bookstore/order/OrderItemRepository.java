package com.rbdip.bookstore.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            select case when count(i) > 0 then true else false end
            from OrderItem i
            where i.product.id = :productId
              and i.order.customer.firstName = :firstName
              and i.order.customer.lastName = :lastName""")
    boolean existsPurchase(
            @Param("productId") Long productId,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName);
}
