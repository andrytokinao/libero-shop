package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    /** The order a basket became, by the name its till gave it -- see {@code Sale#clientReference}. */
    @Query("""
            select i from Invoice i
              join fetch i.sale s
              join fetch s.seller
            where s.clientReference = :reference
            """)
    Optional<Invoice> findBySaleReference(@Param("reference") String reference);

    /**
     * Single entry point for every invoice list in the UI. Null arguments mean "no
     * filter", so one query backs the cash desk, the depot and the admin screens instead
     * of a method per screen.
     *
     * <p>The sale, its seller and its lines are fetched in the same round trip: the
     * responses always carry the line count and the total, and a lazy proxy would turn
     * each row into extra queries.
     *
     * @param openToo with a {@code from}: also the orders older than it that still wait for
     *                someone -- {@code InvoiceAccess#isOpen}, said again here. An unpaid credit
     *                does not leave the till's list because it has aged.
     */
    @Query("""
            select distinct i from Invoice i
              join fetch i.sale s
              join fetch s.seller
              left join fetch s.lines l
              left join fetch l.product
            where (:sellerId is null or s.seller.id = :sellerId)
              and (:paymentStatus is null or i.paymentStatus = :paymentStatus)
              and (:deliveryStatus is null or i.deliveryStatus = :deliveryStatus)
              and (:from is null or i.invoiceDate >= :from
                   or (:openToo = true and (
                        i.paymentStatus in (com.houssen.liberoshop.entity.PaymentStatus.UNPAID,
                                            com.houssen.liberoshop.entity.PaymentStatus.COLLECTED,
                                            com.houssen.liberoshop.entity.PaymentStatus.REMITTED)
                        or i.deliveryStatus in (com.houssen.liberoshop.entity.DeliveryStatus.PENDING,
                                                com.houssen.liberoshop.entity.DeliveryStatus.IN_PROGRESS))))
              and (:to is null or i.invoiceDate < :to)
            order by i.invoiceDate desc, i.id desc
            """)
    List<Invoice> search(@Param("sellerId") Long sellerId,
                         @Param("paymentStatus") PaymentStatus paymentStatus,
                         @Param("deliveryStatus") DeliveryStatus deliveryStatus,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to,
                         @Param("openToo") boolean openToo);

    @Query("""
            select i from Invoice i
              join fetch i.sale s
              join fetch s.seller
              left join fetch s.lines l
              left join fetch l.product
            where i.id = :id
            """)
    Optional<Invoice> findDetailedById(@Param("id") Long id);

    long countByDeliveryStatus(DeliveryStatus deliveryStatus);

    long countByDeliveryStatusIn(java.util.Collection<DeliveryStatus> deliveryStatuses);

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query("select coalesce(sum(i.sale.totalAmount), 0) from Invoice i where i.paymentStatus = :status")
    java.math.BigDecimal sumAmountByPaymentStatus(@Param("status") PaymentStatus status);
}
