package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    Optional<DiningTable> findByToken(String token);

    boolean existsByNameIgnoreCase(String name);

    /** Another table already called that, whatever the case, apart from this one. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<DiningTable> findAllByOrderByNameAsc();
}
