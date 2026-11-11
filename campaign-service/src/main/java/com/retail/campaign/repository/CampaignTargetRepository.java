package com.retail.campaign.repository;

import com.retail.campaign.domain.CampaignStatus;
import com.retail.campaign.domain.CampaignTarget;
import com.retail.campaign.domain.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CampaignTargetRepository extends JpaRepository<CampaignTarget, Long> {

    /** One row per (live campaign, matching target). The id lists must not be empty: callers pass a placeholder. */
    @Query("select new com.retail.campaign.repository.ActiveOffer(c.id, c.name, c.discountType, c.discountValue, "
            + "t.targetType, t.targetId) from CampaignTarget t join t.campaign c "
            + "where c.status in :statuses and c.startAt <= :now and c.endAt > :now "
            + "and ((t.targetType = :productType and t.targetId in :productIds) "
            + "or (t.targetType = :categoryType and t.targetId in :categoryIds))")
    List<ActiveOffer> findActiveOffers(@Param("statuses") Collection<CampaignStatus> statuses,
                                       @Param("now") Instant now,
                                       @Param("productType") TargetType productType,
                                       @Param("productIds") Collection<Long> productIds,
                                       @Param("categoryType") TargetType categoryType,
                                       @Param("categoryIds") Collection<Long> categoryIds);
}