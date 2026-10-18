package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.CatalogChange;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CatalogChangeRepository extends JpaRepository<CatalogChange, Long> {

    /** A product's latest changes, newest first, with who made them. */
    @Query("""
            select c from CatalogChange c
              left join fetch c.changedBy
            where c.product.id = :productId
            order by c.changedAt desc, c.id desc
            """)
    List<CatalogChange> findLatestOf(@Param("productId") Long productId, Pageable page);
}
