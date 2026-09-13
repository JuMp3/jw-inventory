package com.gp.jwinventory.repository;

import com.gp.jwinventory.entity.Congregation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CongregationRepository extends JpaRepository<Congregation, Long> {

    List<Congregation> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
