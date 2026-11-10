package com.retail.review.repository;

import com.retail.review.domain.Review;
import com.retail.review.domain.ReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByProductIdAndStatusAndRating(Long productId, ReviewStatus status, int rating, Pageable pageable);

    List<Review> findByCustomerIdAndProductIdAndStatusNotOrderByIdDesc(Long customerId, Long productId, ReviewStatus status);

    /** Orders of this customer that already have a live (not deleted) review for the product. */
    @Query("select r.orderId from Review r where r.customerId = :customerId and r.productId = :productId "
            + "and r.status <> :deleted")
    List<Long> findReviewedOrderIds(@Param("customerId") Long customerId, @Param("productId") Long productId,
                                    @Param("deleted") ReviewStatus deleted);

    /** SELECT ... FOR UPDATE: changes to one review (and the summary deltas they cause) are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id and r.productId = :productId and r.customerId = :customerId")
    Optional<Review> findOwnedForUpdate(@Param("id") Long id, @Param("productId") Long productId,
                                        @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id and r.productId = :productId")
    Optional<Review> findForUpdate(@Param("id") Long id, @Param("productId") Long productId);

    @Query("select new com.retail.review.repository.RatingCount(r.rating, count(r)) from Review r "
            + "where r.productId = :productId and r.status = :status group by r.rating")
    List<RatingCount> countByRating(@Param("productId") Long productId, @Param("status") ReviewStatus status);
}