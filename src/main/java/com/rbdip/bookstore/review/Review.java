package com.rbdip.bookstore.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// JPA-сущность без собственного поведения - хранилище данных по определению.
@SuppressWarnings("PMD.DataClass")
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "author_name")
    private String authorName;

    @Column(nullable = false)
    private Integer rating;

    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Review() {
        // for JPA
    }

    public Review(Long productId, String authorName, Integer rating, String comment) {
        this.productId = productId;
        this.authorName = authorName;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }
}
