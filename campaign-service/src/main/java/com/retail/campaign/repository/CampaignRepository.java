package com.retail.campaign.repository;

import com.retail.campaign.domain.Campaign;
import com.retail.campaign.domain.CampaignStatus;
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

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);

    /** SELECT ... FOR UPDATE: an edit and the lifecycle job can never interleave on one campaign. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findForUpdate(@Param("id") Long id);

    @Query("select c from Campaign c where c.status in :statuses and c.startAt <= :now and c.endAt > :now "
            + "order by c.endAt asc, c.id asc")
    List<Campaign> findLive(@Param("statuses") Collection<CampaignStatus> statuses, @Param("now") Instant now,
                            Pageable pageable);

    @Query("select c.id from Campaign c where c.status = :status and c.startAt <= :now and c.endAt > :now order by c.id")
    List<Long> findIdsToActivate(@Param("status") CampaignStatus status, @Param("now") Instant now, Pageable pageable);

    @Query("select c.id from Campaign c where c.status in :statuses and c.endAt <= :now order by c.id")
    List<Long> findIdsToExpire(@Param("statuses") Collection<CampaignStatus> statuses, @Param("now") Instant now,
                               Pageable pageable);

    /** SCHEDULED to ACTIVE, only while the campaign is really inside its window. 0 = already done or not due. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Campaign c set c.status = :to, c.updatedAt = :now where c.id = :id and c.status = :from "
            + "and c.startAt <= :now and c.endAt > :now")
    int activateIfDue(@Param("id") Long id, @Param("from") CampaignStatus from, @Param("to") CampaignStatus to,
                      @Param("now") Instant now);

    /** SCHEDULED or ACTIVE to EXPIRED, only when the end time has really passed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Campaign c set c.status = :to, c.updatedAt = :now where c.id = :id and c.status in :from "
            + "and c.endAt <= :now")
    int expireIfEnded(@Param("id") Long id, @Param("from") Collection<CampaignStatus> from,
                      @Param("to") CampaignStatus to, @Param("now") Instant now);
}