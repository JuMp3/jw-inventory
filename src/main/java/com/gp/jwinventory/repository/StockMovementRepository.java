package com.gp.jwinventory.repository;

import com.gp.jwinventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @EntityGraph(attributePaths = "publication")
    Page<StockMovement> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "publication")
    Page<StockMovement> findByPublicationIdOrderByCreatedAtDescIdDesc(Long publicationId, Pageable pageable);

    List<StockMovement> findByPublicationIdOrderByIdAsc(Long publicationId);
}
