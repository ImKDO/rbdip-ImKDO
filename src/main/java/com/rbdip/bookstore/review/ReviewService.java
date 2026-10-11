package com.rbdip.bookstore.review;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отзывы на товары. Факт покупки проверяется через контракт
 * PurchaseVerifier, а не прямым обращением к репозиториям заказов.
 */
@Service
public class ReviewService {

    static final String ANONYMOUS_AUTHOR = "anonymous";

    private final ReviewRepository reviewRepository;
    private final PurchaseVerifier purchaseVerifier;

    public ReviewService(ReviewRepository reviewRepository, PurchaseVerifier purchaseVerifier) {
        this.reviewRepository = reviewRepository;
        this.purchaseVerifier = purchaseVerifier;
    }

    @Transactional
    public SubmittedReview addReview(Long productId, String authorName, Integer rating, String comment) {
        if (rating == null) {
            throw new IllegalArgumentException("rating is required");
        }
        String author = authorName == null ? ANONYMOUS_AUTHOR : authorName;
        Review review = reviewRepository.save(new Review(productId, author, rating, comment));
        boolean verifiedPurchase = authorName != null && purchaseVerifier.hasPurchased(authorName, productId);
        return new SubmittedReview(review.getId(), verifiedPurchase);
    }

    @Transactional(readOnly = true)
    public List<Review> listReviews(Long productId) {
        return reviewRepository.findByProductId(productId);
    }
}
