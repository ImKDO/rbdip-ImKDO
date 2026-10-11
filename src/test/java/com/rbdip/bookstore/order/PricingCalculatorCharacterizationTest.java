package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbdip.bookstore.order.PricingCalculator.LineItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Характеризационные тесты: фиксируют фактическое поведение расчёта цены
 * (включая спорные места - порядок скидок, обнуление отрицательного итога,
 * строгие границы), чтобы рефакторинг калькулятора его не изменил.
 */
class PricingCalculatorCharacterizationTest {

    private final PricingCalculator calculator = new PricingCalculator();

    private BigDecimal total(List<LineItem> items, String customerType, String couponCode) {
        return calculator.calculateOrderTotal(items, customerType, couponCode);
    }

    private static List<LineItem> single(String price, int quantity) {
        return List.of(new LineItem(new BigDecimal(price), quantity));
    }

    @Nested
    @DisplayName("Базовая сумма")
    class BaseTotal {

        @Test
        void emptyOrderCostsZeroWithTwoDecimalPlaces() {
            assertThat(total(List.of(), "regular", null)).isEqualTo(new BigDecimal("0.00"));
        }

        @Test
        void sumsAllLinesAsPriceTimesQuantity() {
            List<LineItem> items = List.of(
                    new LineItem(new BigDecimal("10.00"), 2),
                    new LineItem(new BigDecimal("5.50"), 3));

            assertThat(total(items, "regular", null)).isEqualTo(new BigDecimal("36.50"));
        }

        @Test
        void resultIsAlwaysScaledToTwoDecimalPlaces() {
            assertThat(total(single("7", 3), "regular", null)).isEqualTo(new BigDecimal("21.00"));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "0.125, 0.13",
            "0.124, 0.12",
            "0.135, 0.14",
        })
        void roundsHalfUp(String price, String expected) {
            assertThat(total(single(price, 1), "regular", null)).isEqualTo(new BigDecimal(expected));
        }
    }

    @Nested
    @DisplayName("Скидка за объём позиции")
    class VolumeDiscount {

        @ParameterizedTest(name = "quantity={0} -> {1}")
        @CsvSource({
            "10, 10.00",
            "11, 10.45",
            "20, 19.00",
        })
        void appliesFivePercentOnlyWhenQuantityIsAboveTen(int quantity, String expected) {
            assertThat(total(single("1.00", quantity), "regular", null)).isEqualTo(new BigDecimal(expected));
        }

        @Test
        void isCalculatedPerLineNotPerOrder() {
            List<LineItem> items = List.of(
                    new LineItem(new BigDecimal("1.00"), 6),
                    new LineItem(new BigDecimal("1.00"), 6));

            assertThat(total(items, "regular", null)).isEqualTo(new BigDecimal("12.00"));
        }

        @Test
        void appliesOnlyToTheBulkLine() {
            List<LineItem> items = List.of(
                    new LineItem(new BigDecimal("1.00"), 11),
                    new LineItem(new BigDecimal("10.00"), 1));

            assertThat(total(items, "regular", null)).isEqualTo(new BigDecimal("20.45"));
        }
    }

    @Nested
    @DisplayName("Скидка по типу клиента")
    class CustomerTypeDiscount {

        @ParameterizedTest(name = "customerType={0} -> {1}")
        @CsvSource(nullValues = "null", value = {
            "regular, 100.00",
            "vip, 90.00",
            "wholesale, 85.00",
            "null, 100.00",
            "VIP, 100.00",
            "Wholesale, 100.00",
            "gold, 100.00",
            "'', 100.00",
        })
        void dependsOnExactLowercaseType(String customerType, String expected) {
            assertThat(total(single("100.00", 1), customerType, null)).isEqualTo(new BigDecimal(expected));
        }

        @ParameterizedTest(name = "{0} on {1} -> {2}")
        @CsvSource({
            "vip, 0.05, 0.05",
            "wholesale, 33.33, 28.33",
            "vip, 12.35, 11.12",
        })
        void roundsDiscountedTotalHalfUp(String customerType, String price, String expected) {
            assertThat(total(single(price, 1), customerType, null)).isEqualTo(new BigDecimal(expected));
        }
    }

    @Nested
    @DisplayName("Купоны")
    class Coupons {

        @ParameterizedTest(name = "coupon={0} -> {1}")
        @CsvSource(nullValues = "null", value = {
            "SAVE10, 90.00",
            "SAVE20PERCENT, 80.00",
            "null, 100.00",
            "save10, 100.00",
            "SAVE20, 100.00",
            "UNKNOWN, 100.00",
        })
        void areCaseSensitiveAndOnlyTwoCodesAreKnown(String coupon, String expected) {
            assertThat(total(single("100.00", 1), "regular", coupon)).isEqualTo(new BigDecimal(expected));
        }

        @Test
        void areAppliedAfterCustomerDiscount() {
            // (100 * 0.9) - 10 = 80, а не (100 - 10) * 0.9 = 81
            assertThat(total(single("100.00", 1), "vip", "SAVE10")).isEqualTo(new BigDecimal("80.00"));
        }

        @Test
        void percentCouponStacksWithCustomerDiscount() {
            assertThat(total(single("100.00", 1), "wholesale", "SAVE20PERCENT"))
                    .isEqualTo(new BigDecimal("68.00"));
        }

        @ParameterizedTest(name = "SAVE10 on {0} -> {1}")
        @CsvSource({
            "5.00, 0.00",
            "10.00, 0.00",
            "10.01, 0.01",
        })
        void fixedCouponNeverMakesTotalNegative(String price, String expected) {
            assertThat(total(single(price, 1), "regular", "SAVE10")).isEqualTo(new BigDecimal(expected));
        }
    }

    @Nested
    @DisplayName("Скидка за крупный заказ")
    class LargeOrderDiscount {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "1000.00, 1000.00",
            "1000.01, 980.01",
            "2000.00, 1960.00",
        })
        void appliesTwoPercentOnlyAboveOneThousand(String price, String expected) {
            assertThat(total(single(price, 1), "regular", null)).isEqualTo(new BigDecimal(expected));
        }

        @ParameterizedTest(name = "{0} on {1} with {2} -> {3}")
        @CsvSource(nullValues = "null", value = {
            "vip, 1100.00, null, 990.00",
            "vip, 1200.00, null, 1058.40",
            "wholesale, 1200.00, null, 999.60",
            "regular, 1005.00, SAVE10, 995.00",
        })
        void isCheckedAgainstTotalAfterAllOtherDiscounts(
                String customerType, String price, String coupon, String expected) {
            assertThat(total(single(price, 1), customerType, coupon)).isEqualTo(new BigDecimal(expected));
        }
    }

    @Nested
    @DisplayName("Комбинации всех правил")
    class Combinations {

        @Test
        void wholesaleBulkOrderWithPercentCoupon() {
            // 12 * 100 = 1200 -> *0.95 = 1140 -> *0.85 = 969 -> *0.8 = 775.20 (<= 1000)
            assertThat(total(single("100.00", 12), "wholesale", "SAVE20PERCENT"))
                    .isEqualTo(new BigDecimal("775.20"));
        }

        @Test
        void vipBulkOrderWithFixedCouponAndLargeOrderDiscount() {
            // 20 * 100 = 2000 -> *0.95 = 1900 -> *0.9 = 1710 -> -10 = 1700 -> *0.98 = 1666
            assertThat(total(single("100.00", 20), "vip", "SAVE10")).isEqualTo(new BigDecimal("1666.00"));
        }
    }

    @Nested
    @DisplayName("Граничные и некорректные входные данные")
    class EdgeCases {

        @Test
        void zeroQuantityCostsNothing() {
            assertThat(total(single("100.00", 0), "regular", null)).isEqualTo(new BigDecimal("0.00"));
        }

        @Test
        void negativeTotalIsClampedToZero() {
            assertThat(total(single("-10.00", 1), "regular", null)).isEqualTo(new BigDecimal("0.00"));
            assertThat(total(single("10.00", -2), "regular", null)).isEqualTo(new BigDecimal("0.00"));
        }

        @Test
        void negativeLineReducesTotalOfOtherLines() {
            List<LineItem> items = List.of(
                    new LineItem(new BigDecimal("50.00"), 1),
                    new LineItem(new BigDecimal("-20.00"), 1));

            assertThat(total(items, "regular", null)).isEqualTo(new BigDecimal("30.00"));
        }

        @Test
        void nullItemsAreRejected() {
            assertThatThrownBy(() -> total(null, "regular", null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void nullPriceIsRejected() {
            assertThatThrownBy(() -> total(List.of(new LineItem(null, 1)), "regular", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
