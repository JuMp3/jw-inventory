package com.gp.jwinventory.repository;

import com.gp.jwinventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @EntityGraph(attributePaths = "publication")
    List<InventoryItem> findBySessionId(Long sessionId);

    /** Without the entity graph: publications stay unloaded so they can be read fresh under lock. */
    List<InventoryItem> findBySessionIdOrderById(Long sessionId);

    Optional<InventoryItem> findBySessionIdAndPublicationId(Long sessionId, Long publicationId);
}
