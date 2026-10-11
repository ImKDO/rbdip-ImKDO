package com.rbdip.bookstore.customer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // null в address/phone превращается в IS NULL - так же сравнивает миграция V2.
    // Аргументы derived-запроса Spring Data обязаны соответствовать полям 1:1.
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    Optional<Customer> findFirstByFirstNameAndLastNameAndAddressAndPhoneOrderByIdAsc(
            String firstName, String lastName, String address, String phone);
}
