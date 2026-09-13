package com.gp.jwinventory.repository;

import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "congregation")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    Optional<User> findByRole(Role role);

    @EntityGraph(attributePaths = "congregation")
    List<User> findAllByOrderByFullNameAsc();

    @EntityGraph(attributePaths = "congregation")
    List<User> findByCongregationIdOrderByFullNameAsc(Long congregationId);

    long countByCongregationId(Long congregationId);
}
