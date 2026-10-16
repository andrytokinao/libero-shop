package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.UnitModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitModelRepository extends JpaRepository<UnitModel, Long> {

    @EntityGraph(attributePaths = "units")
    List<UnitModel> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
