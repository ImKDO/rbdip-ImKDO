package com.rbdip.bookstore.review;

/** Результат добавления отзыва: verifiedPurchase - автор действительно покупал товар. */
public record SubmittedReview(Long id, boolean verifiedPurchase) {
}
