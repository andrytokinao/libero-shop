package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * What a depot agent has collected on hand-over and not brought to the desk yet.
     *
     * <p>Told by the order's status rather than by the collector's role: an account that is both
     * cashier and storekeeper also collects at the desk, and that money is already in the till.
     */
    @Query("""
            select p from Payment p
              join fetch p.invoice i
              join fetch i.sale
            where p.collectedBy.id = :collectorId and p.cashRemittance is null
              and i.paymentStatus = com.houssen.liberoshop.entity.PaymentStatus.COLLECTED
            order by p.paymentDate
            """)
    List<Payment> findUnremittedByCollector(@Param("collectorId") Long collectorId);

    /** Same thing across every depot agent, for the admin control screen. */
    @Query("""
            select p from Payment p
              join fetch p.collectedBy u
              join fetch p.invoice i
            where p.cashRemittance is null
              and i.paymentStatus = com.houssen.liberoshop.entity.PaymentStatus.COLLECTED
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

    @Query("""
            select p from Payment p
              join fetch p.invoice i
              join fetch i.sale
            where p.cashRemittance.id = :remittanceId
            order by p.paymentDate
            """)
    List<Payment> findByCashRemittanceId(@Param("remittanceId") Long remittanceId);

    /** Money in the till only: cash still at the depot, or awaiting confirmation, is left out. */
    @Query("""
            select p.paymentMethod, count(p), coalesce(sum(p.amount), 0)
            from Payment p
            where p.paymentDate >= :from and p.paymentDate < :to
              and p.invoice.paymentStatus = com.houssen.liberoshop.entity.PaymentStatus.PAID
            group by p.paymentMethod
            order by sum(p.amount) desc
            """)
    List<Object[]> aggregateByMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.cashRemittance is null
              and p.invoice.paymentStatus = com.houssen.liberoshop.entity.PaymentStatus.COLLECTED
            """)
    BigDecimal sumUnremittedDepotCash();
}
