package com.retail.inventory.repository;

import com.retail.inventory.domain.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProductId(Long productId);

    List<InventoryItem> findByProductIdIn(Collection<Long> productIds);

    /** SELECT ... FOR UPDATE: used by admin changes, so they cannot interleave with a reservation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findForUpdate(@Param("productId") Long productId);

    @Query("select i from InventoryItem i where (i.quantityOnHand - i.quantityReserved) <= i.reorderLevel")
    Page<InventoryItem> findLowStock(Pageable pageable);

    /** Reserve: succeeds only while enough is available. 0 = not enough stock (or no stock record). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InventoryItem i set i.quantityReserved = i.quantityReserved + :qty, i.updatedAt = :now "
            + "where i.productId = :productId and (i.quantityOnHand - i.quantityReserved) >= :qty")
    int reserve(@Param("productId") Long productId, @Param("qty") int qty, @Param("now") Instant now);

    /** Give a reservation back (released or expired). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InventoryItem i set i.quantityReserved = i.quantityReserved - :qty, i.updatedAt = :now "
            + "where i.productId = :productId and i.quantityReserved >= :qty")
    int unreserve(@Param("productId") Long productId, @Param("qty") int qty, @Param("now") Instant now);

    /** Sale: the reserved units leave the shelf for good. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InventoryItem i set i.quantityOnHand = i.quantityOnHand - :qty, "
            + "i.quantityReserved = i.quantityReserved - :qty, i.updatedAt = :now "
            + "where i.productId = :productId and i.quantityReserved >= :qty")
    int commitSale(@Param("productId") Long productId, @Param("qty") int qty, @Param("now") Instant now);

    /** A committed order is cancelled: the units go back on the shelf. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InventoryItem i set i.quantityOnHand = i.quantityOnHand + :qty, i.updatedAt = :now "
            + "where i.productId = :productId")
    int restock(@Param("productId") Long productId, @Param("qty") int qty, @Param("now") Instant now);
}