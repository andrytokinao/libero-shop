package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.SupplyCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface SupplyChargeRepository extends JpaRepository<SupplyCharge, Long> {
    @Query("""
            select distinct c from SupplyCharge c
            left join fetch c.supplys
            where c.deliveryNote.id = :noteId
            order by c.id
            """)
    List<SupplyCharge> findOfNote(@Param("noteId") Long noteId);
}
