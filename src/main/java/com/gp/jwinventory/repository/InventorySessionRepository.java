package com.gp.jwinventory.repository;

import com.gp.jwinventory.entity.InventorySession;
import com.gp.jwinventory.enums.InventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventorySessionRepository extends JpaRepository<InventorySession, Long> {

    Optional<InventorySession> findByStatus(InventoryStatus status);

    List<InventorySession> findByStatusOrderByClosedAtDesc(InventoryStatus status);
}
