package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /** How many sales each seller recorded over a window, as {@code [sellerId, count]}. */
    @Query("select s.seller.id, count(s) from Sale s where s.saleDate >= :from and s.saleDate < :to"
            + " group by s.seller.id")
    List<Object[]> countBySellerBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Total of the sales of one payment status over a window. */
    @Query("""
            select coalesce(sum(s.totalAmount), 0) from Sale s
            where s.paymentStatus = :status and s.saleDate >= :from and s.saleDate < :to
            """)
    BigDecimal sumTotalByStatus(@Param("status") PaymentStatus status,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}
