package com.rbdip.bookstore.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Клиенты с одинаковыми именем и контактами - одна запись в customers,
 * а не копия данных в каждом заказе.
 */
@Service
public class CustomerRegistry {

    private final CustomerRepository customerRepository;

    public CustomerRegistry(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer findOrRegister(String fullName, String address, String phone) {
        CustomerName name = CustomerName.parse(fullName);
        return customerRepository
                .findFirstByFirstNameAndLastNameAndAddressAndPhoneOrderByIdAsc(
                        name.firstName(), name.lastName(), address, phone)
                .orElseGet(() -> customerRepository.save(new Customer(name, address, phone)));
    }
}
