package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.StockMovement;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.Supply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One repository for the whole {@code stock_movement} single table. The subtype queries
 * below let JPA filter on the discriminator, so adding a third movement type later means
 * adding a method here rather than reshaping the storage.
 */
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * The supplier is joined with a LEFT join and must stay that way: an entry written by a
     * product import has none, and an inner join would drop those rows from the supplies screen
     * while their units still showed up in the stock.
     */
    @Query("""
            select s from Supply s
              join fetch s.product
              left join fetch s.supplier
              join fetch s.performedBy
            order by s.movementDate desc, s.id desc
            """)
    List<Supply> findSupplies();

    @Query("""
            select o from StockOutput o
              join fetch o.product
              join fetch o.invoice i
              join fetch o.performedBy
            order by o.movementDate desc, o.id desc
            """)
    List<StockOutput> findOutputs();

    @Query("select count(s) from Supply s where s.movementDate >= :from")
    long countSuppliesSince(@Param("from") LocalDateTime from);

    @Query("select coalesce(sum(s.quantity), 0) from Supply s where s.movementDate >= :from")
    long sumSuppliedUnitsSince(@Param("from") LocalDateTime from);

    /**
     * Deliveries and units per supplier. Supplier-less entries -- the ones an import writes --
     * are excluded rather than grouped: they belong to no supplier, and a nameless bucket in a
     * list of suppliers would be read as a supplier whose name went missing.
     */
    @Query("""
            select o.supplier.id, count(o), coalesce(sum(o.quantity), 0)
            from Supply o where o.supplier is not null group by o.supplier.id
            """)
    List<Object[]> aggregateSuppliesBySupplier();
}
