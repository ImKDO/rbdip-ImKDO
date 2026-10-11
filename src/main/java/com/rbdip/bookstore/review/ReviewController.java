package com.rbdip.bookstore.review;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmittedReview addReview(@PathVariable Long productId, @RequestBody AddReviewRequest request) {
        return reviewService.addReview(productId, request.authorName(), request.rating(), request.comment());
    }

    @GetMapping("/products/{productId}/reviews")
    public List<ReviewView> listReviews(@PathVariable Long productId) {
        return reviewService.listReviews(productId).stream().map(ReviewView::from).toList();
    }

    public record AddReviewRequest(String authorName, Integer rating, String comment) {
    }

    public record ReviewView(String authorName, Integer rating, String comment) {

        static ReviewView from(Review review) {
            return new ReviewView(
                    review.getAuthorName(),
                    review.getRating(),
                    review.getComment() == null ? "" : review.getComment());
        }
    }
}
