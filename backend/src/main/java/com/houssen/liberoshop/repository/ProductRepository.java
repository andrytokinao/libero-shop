package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Rows written before {@code searchText} existed -- see {@code SearchTextBackfill}. */
    List<Product> findBySearchTextIsNull();

    @Query("select p from Product p left join fetch p.category where p.id in :ids")
    List<Product> findAllWithCategoryByIdIn(@Param("ids") Collection<Long> ids);

    Optional<Product> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeIsNotNull();

    /** A purchase price known for at least one product. */
    boolean existsByAverageCostIsNotNull();

    boolean existsByStockQuantityGreaterThan(BigDecimal quantity);

    /** Whether a product is already called that, capitals aside. */
    boolean existsByNameIgnoreCase(String name);

    /** {@link #findByBarcode} with the rayon already loaded, for a response built straight away. */
    @Query("select p from Product p left join fetch p.category where p.barcode = :barcode")
    Optional<Product> findWithCategoryByBarcode(@Param("barcode") String barcode);

    @Query("select p from Product p left join fetch p.category order by p.name")
    List<Product> findAllWithCategory();

    /**
     * The catalogue as the lists filter it, filtered by the database rather than loaded whole.
     *
     * @param anyRayon  true to ignore {@code rayons}
     * @param rayons    the rayon picked and everything below it; never empty
     * @param lowOnly   true to keep the references under their alert threshold
     * @param threshold the threshold of a product that sets none
     * @param needle    a {@code like} pattern on name, barcode or rayon name, or null for all
     */
    @Query("""
            select p from Product p left join fetch p.category c
            where (:anyRayon = true or c.id in :rayons)
              and (:lowOnly = false or p.stockQuantity < coalesce(p.lowStockThreshold, :threshold))
              and (:needle is null
                   or lower(p.name) like :needle escape '\\'
                   or lower(p.barcode) like :needle escape '\\'
                   or lower(c.name) like :needle escape '\\')
            order by p.name
            """)
    List<Product> findFiltered(@Param("anyRayon") boolean anyRayon, @Param("rayons") Collection<Long> rayons,
                               @Param("lowOnly") boolean lowOnly, @Param("threshold") BigDecimal threshold,
                               @Param("needle") String needle);

    /**
     * The whole stock at its sale price and in base units, summed by the database -- what the
     * dashboards show at the top -- as {@code [value, units]}. Each product's value is rounded to
     * the cent before adding, as the lists show it, so the total matches their sum.
     */
    @Query("select coalesce(sum(round(p.price * p.stockQuantity, 2)), 0), coalesce(sum(p.stockQuantity), 0)"
            + " from Product p")
    List<Object[]> sumStock();

    /** How many are in alert: counted by the database, not by loading them. */
    @Query("select count(p) from Product p where p.stockQuantity < coalesce(p.lowStockThreshold, :threshold)")
    long countLowStock(@Param("threshold") BigDecimal threshold);

    /** Below its own threshold, or below {@code threshold} for a product that has none. */
    @Query("select p from Product p left join fetch p.category"
            + " where p.stockQuantity < coalesce(p.lowStockThreshold, :threshold) order by p.stockQuantity")
    List<Product> findLowStock(@Param("threshold") BigDecimal threshold);

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
     * How many products each rayon holds directly, as {@code [categoryId, count]} -- one grouped
     * query for every folder of an explorer page, rather than a count per folder.
     */
    @Query("select p.category.id, count(p) from Product p where p.category is not null and p.retired = false"
            + " group by p.category.id")
    List<Object[]> countByCategory();

    /**
     * Pessimistic read-for-update, used while a sale or a supply moves the stock.
     * Two cash desks selling the last unit at the same instant would otherwise both
     * succeed on a stale quantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
