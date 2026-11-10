package com.retail.review.repository;

import com.retail.review.domain.ProductRatingSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductRatingSummaryRepository extends JpaRepository<ProductRatingSummary, Long> {

    /**
     * Creates the row on the first review, otherwise adds the deltas, and always bumps the version.
     * One atomic statement: parallel reviews of the same product cannot lose each other's change.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO product_rating_summary (product_id, review_count, rating_sum, change_version, synced_version, updated_at) "
            + "VALUES (:productId, :countDelta, :sumDelta, 1, 0, :now) "
            + "ON DUPLICATE KEY UPDATE review_count = review_count + :countDelta, rating_sum = rating_sum + :sumDelta, "
            + "change_version = change_version + 1, updated_at = :now", nativeQuery = true)
    int applyDelta(@Param("productId") Long productId, @Param("countDelta") int countDelta,
                   @Param("sumDelta") long sumDelta, @Param("now") Instant now);

    /** Only ever moves forward. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductRatingSummary s set s.syncedVersion = :version where s.productId = :id and s.syncedVersion < :version")
    int markSynced(@Param("id") Long id, @Param("version") long version);

    @Query("select s from ProductRatingSummary s where s.changeVersion > s.syncedVersion order by s.updatedAt")
    List<ProductRatingSummary> findUnsynced(Pageable pageable);
}