package com.retail.order.repository;

import com.retail.order.domain.CustomerOrder;
import com.retail.order.domain.OrderStatus;
import com.retail.order.dto.PurchaseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Optional<CustomerOrder> findByIdAndCustomerId(Long id, Long customerId);

    Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    List<CustomerOrder> findByStatusAndUpdatedAtBefore(OrderStatus status, Instant cutoff, Pageable pageable);

    @Query("select o from CustomerOrder o where (o.refundDue = true or o.stockReturnDue = true or o.cartCheckoutDue = true) "
            + "and o.compensationAttempts < :max and o.updatedAt < :cutoff order by o.id")
    List<CustomerOrder> findNeedingCompensation(@Param("max") int max, @Param("cutoff") Instant cutoff, Pageable pageable);

    /** Paid and stock committed: the order is confirmed, and the cart must still be closed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.status = :to, o.cartCheckoutDue = true, o.updatedAt = :now "
            + "where o.id = :id and o.status = :from")
    int confirm(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.status = :to, o.failureReason = :reason, o.refundDue = :refundDue, "
            + "o.stockReturnDue = :stockDue, o.updatedAt = :now where o.id = :id and o.status = :from")
    int fail(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to,
             @Param("reason") String reason, @Param("refundDue") boolean refundDue,
             @Param("stockDue") boolean stockDue, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.status = :to, o.refundDue = true, o.stockReturnDue = true, o.updatedAt = :now "
            + "where o.id = :id and o.status = :from")
    int cancel(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to, @Param("now") Instant now);

    /** Fulfilment step: touches nothing but the status. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.status = :to, o.updatedAt = :now where o.id = :id and o.status = :from")
    int advance(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.paymentId = :paymentId where o.id = :id and o.paymentId is null")
    int recordPayment(@Param("id") Long id, @Param("paymentId") Long paymentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.refundDue = false where o.id = :id")
    int clearRefundDue(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.stockReturnDue = false where o.id = :id")
    int clearStockReturnDue(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.cartCheckoutDue = false where o.id = :id")
    int clearCartCheckoutDue(@Param("id") Long id);

    /** Also moves updatedAt, which gives the retry a natural back-off. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.compensationAttempts = o.compensationAttempts + 1, o.updatedAt = :now where o.id = :id")
    int bumpAttempts(@Param("id") Long id, @Param("now") Instant now);

    /** One row per order of this customer that contains the product and is in one of the given statuses. */
    @Query("select new com.retail.order.dto.PurchaseResponse(o.id, o.status) from CustomerOrder o join o.items i "
            + "where o.customerId = :customerId and i.productId = :productId and o.status in :statuses order by o.id")
    List<PurchaseResponse> findPurchases(@Param("customerId") Long customerId, @Param("productId") Long productId,
                                         @Param("statuses") Collection<OrderStatus> statuses);
}