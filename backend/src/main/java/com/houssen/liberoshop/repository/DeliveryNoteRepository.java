package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.DeliveryNote;
import org.springframework.data.jpa.repository.JpaRepository;
import com.houssen.liberoshop.entity.StockLoss;
import com.houssen.liberoshop.entity.Supply;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryNoteRepository extends JpaRepository<DeliveryNote, Long> {
    @Query("""
            select n from DeliveryNote n
            left join fetch n.supplier
            order by n.date desc, n.id desc
            """)
    List<DeliveryNote> findLatest(Pageable pageable);

    @Query("""
            select n from DeliveryNote n
            left join fetch n.supplier
            where n.supplier.id = :supplierId
            order by n.date desc, n.id desc
            """)
    List<DeliveryNote> findLatestOf(@Param("supplierId") Long supplierId, Pageable pageable);

    @Query("""
            select n from DeliveryNote n
            left join fetch n.supplier
            where n.id = :id
            """)
    Optional<DeliveryNote> findWithSupplier(@Param("id") Long id);

    @Query("""
            select s from Supply s
            join fetch s.product
            where s.deliveryNote.id = :noteId
            order by s.id
            """)
    List<Supply> findSuppliesOf(@Param("noteId") Long noteId);

    @Query("""
            select l from StockLoss l
            join fetch l.product
            where l.supply.deliveryNote.id = :noteId
            order by l.id
            """)
    List<StockLoss> findLossesOf(@Param("noteId") Long noteId);

    @Query("""
            select s.deliveryNote.id, count(s) from Supply s
            where s.deliveryNote.id in :ids
            group by s.deliveryNote.id
            """)
    List<Object[]> countLines(@Param("ids") Collection<Long> ids);

    @Query("""
            select l.supply.deliveryNote.id, count(l) from StockLoss l
            where l.supply.deliveryNote.id in :ids
            group by l.supply.deliveryNote.id
            """)
    List<Object[]> countLosses(@Param("ids") Collection<Long> ids);

    @Query("select s.deliveryNote.id from Supply s where s.id = :supplyId")
    Optional<Long> findNoteIdOfSupply(@Param("supplyId") Long supplyId);
}
