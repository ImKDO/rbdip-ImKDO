package com.rbdip.bookstore.review;

/**
 * Явный контракт: что модулю отзывов нужно знать о покупках. Интерфейс
 * принадлежит review, а реализацию предоставляет модуль заказов, поэтому
 * review не зависит ни от одного класса пакета order.
 */
public interface PurchaseVerifier {

    boolean hasPurchased(String customerFullName, Long productId);
}
