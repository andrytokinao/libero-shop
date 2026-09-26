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

    /**
     * Single entry point for every invoice list in the UI. Null arguments mean "no
     * filter", so one query backs the cash desk, the depot and the admin screens instead
     * of a method per screen.
     *
     * <p>The sale, its seller and its lines are fetched in the same round trip: the
     * responses always carry the line count and the total, and a lazy proxy would turn
     * each row into extra queries.
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
              and (:from is null or i.invoiceDate >= :from)
              and (:to is null or i.invoiceDate < :to)
            order by i.invoiceDate desc, i.id desc
            """)
    List<Invoice> search(@Param("sellerId") Long sellerId,
                         @Param("paymentStatus") PaymentStatus paymentStatus,
                         @Param("deliveryStatus") DeliveryStatus deliveryStatus,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

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

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query("select coalesce(sum(i.sale.totalAmount), 0) from Invoice i where i.paymentStatus = :status")
    java.math.BigDecimal sumAmountByPaymentStatus(@Param("status") PaymentStatus status);
}
