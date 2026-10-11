package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Тип клиента и его персональная скидка. Код сравнивается с учётом
 * регистра; неизвестный тип означает "без скидки".
 */
public enum CustomerType {

    REGULAR("regular", BigDecimal.ONE),
    VIP("vip", new BigDecimal("0.9")),
    WHOLESALE("wholesale", new BigDecimal("0.85"));

    private final String code;
    private final BigDecimal priceMultiplier;

    CustomerType(String code, BigDecimal priceMultiplier) {
        this.code = code;
        this.priceMultiplier = priceMultiplier;
    }

    public String getCode() {
        return code;
    }

    public static Optional<CustomerType> fromCode(String code) {
        return Arrays.stream(values()).filter(type -> type.code.equals(code)).findFirst();
    }

    BigDecimal applyDiscount(BigDecimal total) {
        return total.multiply(priceMultiplier);
    }
}
