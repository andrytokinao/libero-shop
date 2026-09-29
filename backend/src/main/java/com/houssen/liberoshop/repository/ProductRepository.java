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
     * What the stock on hand is worth, per rayon, summed by the database.
     *
     * <p>One row per rayon, null for the products filed under none:
     * {@code [categoryId, references, units, saleValue, costValue, costedSaleValue, uncostedUnits]}.
     * {@code costValue} is the stock at its weighted average cost; products never costed are
     * kept out of it and out of {@code costedSaleValue}, so the potential margin is computed
     * only where both sides are known. Empty shelves are left out: they are worth nothing either
     * way and would only swell the reference count.
     */
    @Query("""
            select c.id,
                   count(p),
                   sum(p.stockQuantity),
                   sum(p.stockQuantity * p.price),
                   sum(case when p.averageCost is null then 0 else p.stockQuantity * p.averageCost end),
                   sum(case when p.averageCost is null then 0 else p.stockQuantity * p.price end),
                   sum(case when p.averageCost is null then p.stockQuantity else 0 end)
            from Product p left join p.category c
            where p.stockQuantity > 0
            group by c.id
            """)
    List<Object[]> aggregateStockValueByCategory();

    /**
     * Pessimistic read-for-update, used while a sale or a supply moves the stock.
     * Two cash desks selling the last unit at the same instant would otherwise both
     * succeed on a stale quantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
