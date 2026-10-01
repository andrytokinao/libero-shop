package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.SaleLine;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    /**
     * What each product sold for and cost over a window, summed by the database.
     *
     * <p>One row per product: {@code [productId, name, units, revenue, costedRevenue, cost]}.
     * Lines sold without a known cost are counted in {@code revenue} but kept out of both
     * {@code costedRevenue} and {@code cost}, so the margin is computed on the lines that can
     * have one and the rest is reported as such rather than passed off as pure profit.
     *
     * <p>Every sale counts, paid or not: the goods left the shelf either way, and the margin
     * belongs to the day they were sold, not the day they were settled. Cancelled orders do not
     * count: before hand-over their goods went back on the shelf, and after it nothing was earned.
     */
    @Query("""
            select l.product.id, l.product.name,
                   sum(l.quantity),
                   sum(l.unitPrice * l.quantity),
                   sum(case when l.unitCost is null then 0 else l.unitPrice * l.quantity end),
                   sum(case when l.unitCost is null then 0 else l.unitCost * l.quantity end)
            from SaleLine l
            where l.sale.saleDate >= :from and l.sale.saleDate < :to
              and l.sale.paymentStatus <> com.houssen.liberoshop.entity.PaymentStatus.CANCELLED
            group by l.product.id, l.product.name
            """)
    List<Object[]> aggregateMarginByProduct(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    /**
     * The products that left the shelf the most since {@code from}, most units first -- what a
     * till shows before anything is typed. Cancelled orders do not count, for the reason above.
     * Ties are broken by id so the grid does not reshuffle between two identical calls.
     */
    @Query("""
            select l.product.id
            from SaleLine l
            where l.sale.saleDate >= :from
              and l.sale.paymentStatus <> com.houssen.liberoshop.entity.PaymentStatus.CANCELLED
            group by l.product.id
            order by sum(l.quantity) desc, l.product.id
            """)
    List<Long> findBestSellingProductIds(@Param("from") LocalDateTime from, Limit limit);
}
