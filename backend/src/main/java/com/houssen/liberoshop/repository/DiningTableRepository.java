package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    Optional<DiningTable> findByToken(String token);

    boolean existsByNameIgnoreCase(String name);

    List<DiningTable> findAllByOrderByNameAsc();
}
