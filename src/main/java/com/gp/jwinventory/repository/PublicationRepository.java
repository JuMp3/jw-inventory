package com.gp.jwinventory.repository;

import com.gp.jwinventory.entity.Publication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PublicationRepository extends JpaRepository<Publication, Long> {

    List<Publication> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    /** Row lock: concurrent +1/-1 from different users must not lose updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Publication p where p.id = :id")
    Optional<Publication> findByIdForUpdate(@Param("id") Long id);
}
