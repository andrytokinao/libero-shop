package com.houssen.libertyshop.repository;

import com.houssen.libertyshop.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByName(String name);

    List<Supplier> findAllByOrderByNameAsc();
}
