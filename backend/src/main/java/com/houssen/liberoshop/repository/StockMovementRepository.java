package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.StockAdjustment;
import com.houssen.liberoshop.entity.StockMovement;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.Supply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    List<Supply> findLatestSupplies(Pageable limit);

    /** Everything one supplier delivered, newest first: what the supplier's purchases sum up. */
    @Query("""
            select s from Supply s
              join fetch s.product
              join fetch s.supplier sp
              join fetch s.performedBy
            where sp.id = :supplierId
            order by s.movementDate desc, s.id desc
            """)
    List<Supply> findSuppliesOf(@Param("supplierId") Long supplierId);

    /**
     * One page of everything that moved one product's stock, newest first -- receipts, sales and
     * corrections together, which is what explains the figure on its line.
     */
    @Query(value = """
            select m from StockMovement m
              join fetch m.performedBy
            where m.product.id = :productId
            order by m.movementDate desc, m.id desc
            """,
            countQuery = "select count(m) from StockMovement m where m.product.id = :productId")
    Page<StockMovement> findByProduct(@Param("productId") Long productId, Pageable pageable);

    /** The last receipt of one product that stated a cost -- see {@link #findLatestCostedSupplyPerProduct}. */
    @Query("""
            select s from Supply s
              left join fetch s.supplier
            where s.product.id = :productId
              and s.unitCost is not null
              and s.id = (select max(t.id) from Supply t
                          where t.product.id = :productId and t.unitCost is not null)
            """)
    Optional<Supply> findLatestCostedSupplyOf(@Param("productId") Long productId);

    /**
     * One page of the outputs, newest first, the words looked for by the database: an output is
     * written per line sold, so this is the list that grows fastest and it is never read whole.
     *
     * @param needle lower-cased, wrapped in {@code %} and with its own {@code %}, {@code _} and
     *               {@code \} escaped -- see {@code StockService.findOutputs}; null for no filter
     */
    @Query(value = """
            select o from StockOutput o
              join fetch o.product p
              join fetch o.invoice i
              join fetch o.performedBy
            where (:needle is null
                   or lower(p.name) like :needle escape '\\'
                   or lower(i.invoiceNumber) like :needle escape '\\'
                   or lower(i.clientName) like :needle escape '\\')
            order by o.movementDate desc, o.id desc
            """,
            countQuery = """
            select count(o) from StockOutput o join o.product p join o.invoice i
            where (:needle is null
                   or lower(p.name) like :needle escape '\\'
                   or lower(i.invoiceNumber) like :needle escape '\\'
                   or lower(i.clientName) like :needle escape '\\')
            """)
    Page<StockOutput> findOutputs(@Param("needle") String needle, Pageable pageable);

    /**
     * What all the outputs matching {@code needle} add up to, as {@code [units, value]} -- the
     * value each was sold for, like the rows ({@link StockOutput#value()}: the current price only
     * for outputs older than that). Summed here because the screen only holds a page of them.
     * The outputs of a cancelled order are listed, marked, but not counted: the goods came back.
     */
    @Query("""
            select coalesce(sum(o.quantity), 0), coalesce(sum(coalesce(o.saleValue, o.quantity * p.price)), 0)
            from StockOutput o join o.product p join o.invoice i
            where o.cancelledAt is null
              and (:needle is null
                   or lower(p.name) like :needle escape '\\'
                   or lower(i.invoiceNumber) like :needle escape '\\'
                   or lower(i.clientName) like :needle escape '\\')
            """)
    List<Object[]> sumOutputs(@Param("needle") String needle);

    @Query("""
            select a from StockAdjustment a
              join fetch a.product
              join fetch a.performedBy
            order by a.movementDate desc, a.id desc
            """)
    List<StockAdjustment> findLatestAdjustments(Pageable limit);

    /**
     * Every correction counted, and the losses among them summed, as {@code [count, units, value]}.
     * A loss is a correction that took stock away, was not itself an undo, and was not undone;
     * its value is the product's average cost, rounded to the cent per line as the list shows it.
     */
    @Query("""
            select count(a),
                   coalesce(sum(case when a.quantity < 0 and a.reason <> :undo and a.cancelledAt is null
                                     then -a.quantity end), 0),
                   coalesce(sum(case when a.quantity < 0 and a.reason <> :undo and a.cancelledAt is null
                                          and p.averageCost is not null
                                     then -round(p.averageCost * a.quantity, 2) end), 0)
            from StockAdjustment a join a.product p
            """)
    List<Object[]> summarizeAdjustments(@Param("undo") AdjustmentReason undo);

    /** What one order took off the shelves -- given back when it is cancelled before hand-over. */
    @Query("select o from StockOutput o where o.invoice.id = :invoiceId")
    List<StockOutput> findOutputsOfInvoice(@Param("invoiceId") Long invoiceId);

    /**
     * The last receipt of each product that stated a cost -- "the last price I paid". Last in
     * booking order, which is the identifier's: a whole import file shares one timestamp.
     */
    @Query("""
            select s from Supply s
              left join fetch s.supplier
            where s.unitCost is not null
              and s.id = (select max(t.id) from Supply t
                          where t.product = s.product and t.unitCost is not null)
            """)
    List<Supply> findLatestCostedSupplyPerProduct();

    /** Every receipt of one product that stated a cost, newest first. */
    @Query("""
            select s from Supply s
              left join fetch s.supplier
            where s.product.id = :productId and s.unitCost is not null
            order by s.movementDate desc, s.id desc
            """)
    List<Supply> findCostedSuppliesOf(@Param("productId") Long productId);

    @Query("select count(s) from Supply s where s.movementDate >= :from")
    long countSuppliesSince(@Param("from") LocalDateTime from);

    @Query("select coalesce(sum(s.quantity), 0) from Supply s where s.movementDate >= :from")
    BigDecimal sumSuppliedUnitsSince(@Param("from") LocalDateTime from);

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
