package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.ProductPackaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductPackagingRepository extends JpaRepository<ProductPackaging, Long> {

    /** Smallest first, so a list reads kapoka, kg, sac -- the order a shopkeeper counts in. */
    List<ProductPackaging> findByProductIdOrderByFactorAsc(Long productId);

    boolean existsByBarcode(String barcode);

    /** The packaging a scanned code designates -- a sack's own barcode -- with its product. */
    @Query("select p from ProductPackaging p join fetch p.product where p.barcode = :barcode")
    Optional<ProductPackaging> findWithProductByBarcode(@Param("barcode") String barcode);
}
