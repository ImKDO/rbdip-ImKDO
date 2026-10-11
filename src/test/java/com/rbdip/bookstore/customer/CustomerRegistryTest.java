package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerRegistryTest {

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerRegistry registry = new CustomerRegistry(repository);

    @Test
    void reusesExistingCustomerWithSameNameAndContacts() {
        Customer existing = new Customer(new CustomerName("Ivan", "Petrov"), "Moscow", null);
        when(repository.findFirstByFirstNameAndLastNameAndAddressAndPhoneOrderByIdAsc(
                        "Ivan", "Petrov", "Moscow", null))
                .thenReturn(Optional.of(existing));

        assertThat(registry.findOrRegister(" Ivan  Petrov ", "Moscow", null)).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void registersNewCustomerWithParsedName() {
        when(repository.findFirstByFirstNameAndLastNameAndAddressAndPhoneOrderByIdAsc(
                        "Anna", "Maria Sidorova", "SPb", "+7999"))
                .thenReturn(Optional.empty());
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer customer = registry.findOrRegister("Anna Maria Sidorova", "SPb", "+7999");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(repository).save(saved.capture());
        assertThat(customer).isSameAs(saved.getValue());
        assertThat(customer.getName()).isEqualTo(new CustomerName("Anna", "Maria Sidorova"));
        assertThat(customer.getFullName()).isEqualTo("Anna Maria Sidorova");
        assertThat(customer.getAddress()).isEqualTo("SPb");
        assertThat(customer.getPhone()).isEqualTo("+7999");
    }
}
