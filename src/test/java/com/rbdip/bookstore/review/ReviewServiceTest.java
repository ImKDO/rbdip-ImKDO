package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewServiceTest {

    private static final long PRODUCT_ID = 7L;

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final PurchaseVerifier purchaseVerifier = mock(PurchaseVerifier.class);
    private final ReviewService service = new ReviewService(reviewRepository, purchaseVerifier);

    @BeforeEach
    void setUp() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 100L);
            return review;
        });
    }

    @ParameterizedTest(name = "purchased={0}")
    @CsvSource({"true", "false"})
    void savesReviewAndReportsWhetherAuthorBoughtTheProduct(boolean purchased) {
        when(purchaseVerifier.hasPurchased("Anna Sidorova", PRODUCT_ID)).thenReturn(purchased);

        SubmittedReview result = service.addReview(PRODUCT_ID, "Anna Sidorova", 5, "Great");

        assertThat(result).isEqualTo(new SubmittedReview(100L, purchased));
        Review saved = savedReview();
        assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(saved.getAuthorName()).isEqualTo("Anna Sidorova");
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("Great");
    }

    @Test
    void anonymousReviewIsNeverVerified() {
        SubmittedReview result = service.addReview(PRODUCT_ID, null, 4, null);

        assertThat(result.verifiedPurchase()).isFalse();
        assertThat(savedReview().getAuthorName()).isEqualTo("anonymous");
        verifyNoInteractions(purchaseVerifier);
    }

    @Test
    void rejectsReviewWithoutRating() {
        assertThatThrownBy(() -> service.addReview(PRODUCT_ID, "Anna Sidorova", null, "no rating"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rating is required");
        verifyNoInteractions(reviewRepository, purchaseVerifier);
    }

    @Test
    void listsReviewsOfProduct() {
        List<Review> reviews = List.of(new Review(PRODUCT_ID, "Anna Sidorova", 5, null));
        when(reviewRepository.findByProductId(PRODUCT_ID)).thenReturn(reviews);

        assertThat(service.listReviews(PRODUCT_ID)).isEqualTo(reviews);
    }

    private Review savedReview() {
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        return captor.getValue();
    }
}
