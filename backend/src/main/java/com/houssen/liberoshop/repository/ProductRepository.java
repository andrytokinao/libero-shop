package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    @Query("select p from Product p left join fetch p.category order by p.name")
    List<Product> findAllWithCategory();

    @Query("select p from Product p left join fetch p.category where p.stockQuantity < :threshold order by p.stockQuantity")
    List<Product> findLowStock(@Param("threshold") int threshold);

    /**
     * Pessimistic read-for-update, used while a sale or a supply moves the stock.
     * Two cash desks selling the last unit at the same instant would otherwise both
     * succeed on a stale quantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
