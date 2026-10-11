package com.rbdip.bookstore.customer;

import java.util.regex.Pattern;

/**
 * Имя клиента, разбитое на имя и фамилию. Первое слово полного имени -
 * имя, всё остальное - фамилия (так же разбивает данные миграция V2).
 */
public record CustomerName(String firstName, String lastName) {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public static CustomerName parse(String fullName) {
        String[] parts = WHITESPACE.split(fullName.strip(), 2);
        return new CustomerName(parts[0], parts.length > 1 ? parts[1] : "");
    }

    public String fullName() {
        return lastName.isEmpty() ? firstName : firstName + " " + lastName;
    }
}
