package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** API отзывов: отметка "проверенная покупка" приходит из модуля заказов через PurchaseVerifier. */
class ReviewApiTest extends AbstractIntegrationTest {

    private Long productId;

    @BeforeEach
    void productBoughtByAnna() {
        productId = Long.valueOf(restTemplate.postForEntity(
                        "/products", Map.of("name", "Refactoring", "price", new BigDecimal("40.00")), Map.class)
                .getBody().get("id").toString());
        restTemplate.postForEntity("/orders", new CreateOrderRequest(
                "Anna Sidorova", "SPb, Nevsky 10", null, "regular", null,
                List.of(new CreateOrderRequest.Item(productId, 1))), Map.class);
    }

    @Test
    void reviewOfBuyerIsMarkedAsVerifiedPurchase() {
        ResponseEntity<Map> response = addReview("Anna Sidorova", 5, "Great");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("verifiedPurchase", true);
        assertThat(response.getBody().get("id")).isNotNull();
    }

    @Test
    void reviewOfSomeoneWhoDidNotBuyIsNotVerified() {
        assertThat(addReview("Ivan Petrov", 3, null).getBody()).containsEntry("verifiedPurchase", false);
    }

    @Test
    void anonymousReviewIsNotVerifiedAndListedAsAnonymous() {
        assertThat(addReview(null, 4, null).getBody()).containsEntry("verifiedPurchase", false);

        List<Object> reviews = restTemplate.getForObject("/products/" + productId + "/reviews", List.class);

        assertThat(reviews).containsExactly(Map.of("authorName", "anonymous", "rating", 4, "comment", ""));
    }

    @Test
    void reviewWithoutRatingIsRejected() {
        assertThat(addReview("Anna Sidorova", null, "no rating").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> addReview(String authorName, Integer rating, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("authorName", authorName);
        body.put("rating", rating);
        body.put("comment", comment);
        return restTemplate.postForEntity("/products/" + productId + "/reviews", body, Map.class);
    }
}
