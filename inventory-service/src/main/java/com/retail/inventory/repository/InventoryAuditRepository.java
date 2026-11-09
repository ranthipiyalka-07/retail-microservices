package com.retail.inventory.repository;

import com.retail.inventory.domain.InventoryAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, Long> {
    Page<InventoryAudit> findByProductIdOrderByIdDesc(Long productId, Pageable pageable);
}