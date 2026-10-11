package com.rbdip.bookstore.order;

import java.math.BigDecimal;

/** Публикуется после сохранения заказа; слушатели получают его только после коммита. */
public record OrderPlacedEvent(Long orderId, String customerFullName, BigDecimal total) {
}
