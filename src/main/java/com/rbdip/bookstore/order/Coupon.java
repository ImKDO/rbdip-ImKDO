package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Купоны на скидку. Код купона совпадает с именем константы (с учётом
 * регистра); неизвестный купон игнорируется.
 */
enum Coupon {

    SAVE10 {
        @Override
        BigDecimal apply(BigDecimal total) {
            return total.subtract(FIXED_DISCOUNT);
        }
    },

    SAVE20PERCENT {
        @Override
        BigDecimal apply(BigDecimal total) {
            return total.multiply(PERCENT_PRICE_MULTIPLIER);
        }
    };

    private static final BigDecimal FIXED_DISCOUNT = BigDecimal.TEN;
    private static final BigDecimal PERCENT_PRICE_MULTIPLIER = new BigDecimal("0.8");

    abstract BigDecimal apply(BigDecimal total);

    static Optional<Coupon> fromCode(String code) {
        return Arrays.stream(values()).filter(coupon -> coupon.name().equals(code)).findFirst();
    }
}
