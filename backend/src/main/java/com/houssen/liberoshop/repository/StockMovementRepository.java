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

    @Query("""
            select s from Supply s
              join fetch s.product
              join fetch s.supplier
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

    @Query("""
            select o.supplier.id, count(o), coalesce(sum(o.quantity), 0)
            from Supply o group by o.supplier.id
            """)
    List<Object[]> aggregateSuppliesBySupplier();
}
