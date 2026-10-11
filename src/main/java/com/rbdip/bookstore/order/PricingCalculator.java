package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Расчёт итоговой стоимости заказа. Правила применяются строго в таком
 * порядке (зафиксирован характеризационными тестами):
 * скидка за объём позиции -> скидка по типу клиента -> купон ->
 * обнуление отрицательного итога -> скидка за крупный заказ -> округление.
 */
@Component
public class PricingCalculator {

    static final int BULK_QUANTITY_THRESHOLD = 10;
    static final BigDecimal BULK_LINE_PRICE_MULTIPLIER = new BigDecimal("0.95");
    static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    static final BigDecimal LARGE_ORDER_PRICE_MULTIPLIER = new BigDecimal("0.98");
    static final int MONEY_SCALE = 2;

    public record LineItem(BigDecimal price, int quantity) {

        BigDecimal cost() {
            BigDecimal cost = price.multiply(BigDecimal.valueOf(quantity));
            return quantity > BULK_QUANTITY_THRESHOLD ? cost.multiply(BULK_LINE_PRICE_MULTIPLIER) : cost;
        }
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = subtotal(items);
        total = applyCustomerDiscount(total, customerType);
        total = applyCoupon(total, couponCode);
        total = total.max(BigDecimal.ZERO);
        total = applyLargeOrderDiscount(total);
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal subtotal(List<LineItem> items) {
        return items.stream().map(LineItem::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal applyCustomerDiscount(BigDecimal total, String customerType) {
        return CustomerType.fromCode(customerType).map(type -> type.applyDiscount(total)).orElse(total);
    }

    private static BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        return Coupon.fromCode(couponCode).map(coupon -> coupon.apply(total)).orElse(total);
    }

    private static BigDecimal applyLargeOrderDiscount(BigDecimal total) {
        return total.compareTo(LARGE_ORDER_THRESHOLD) > 0 ? total.multiply(LARGE_ORDER_PRICE_MULTIPLIER) : total;
    }
}
