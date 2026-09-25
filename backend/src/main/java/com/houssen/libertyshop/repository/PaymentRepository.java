package com.houssen.libertyshop.repository;

import com.houssen.libertyshop.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** What a depot agent has collected and not handed over yet. */
    @Query("""
            select p from Payment p
              join fetch p.invoice i
              join fetch i.sale
            where p.collectedBy.id = :collectorId and p.cashRemittance is null
            order by p.paymentDate
            """)
    List<Payment> findUnremittedByCollector(@Param("collectorId") Long collectorId);

    /** Same thing across every depot agent, for the admin control screen. */
    @Query("""
            select p from Payment p
              join fetch p.collectedBy u
              join fetch p.invoice i
            where p.cashRemittance is null
              and com.houssen.libertyshop.entity.RoleApp.DEPOT_AGENT member of u.roles
            order by p.paymentDate
            """)
    List<Payment> findUnremittedDepotCash();

    @Query("""
            select p from Payment p
              join fetch p.invoice i
            where p.collectedBy.id = :collectorId and p.paymentDate >= :from and p.paymentDate < :to
            order by p.paymentDate desc
            """)
    List<Payment> findByCollectorAndPeriod(@Param("collectorId") Long collectorId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    List<Payment> findByCashRemittanceId(Long remittanceId);

    @Query("""
            select p.paymentMethod, count(p), coalesce(sum(p.amount), 0)
            from Payment p
            where p.paymentDate >= :from and p.paymentDate < :to
            group by p.paymentMethod
            order by sum(p.amount) desc
            """)
    List<Object[]> aggregateByMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
              join p.collectedBy u
            where p.cashRemittance is null
              and com.houssen.libertyshop.entity.RoleApp.DEPOT_AGENT member of u.roles
            """)
    BigDecimal sumUnremittedDepotCash();
}
