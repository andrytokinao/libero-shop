package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.ProductPackaging;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductPackagingRepository extends JpaRepository<ProductPackaging, Long> {

    /** Smallest first, so a list reads kapoka, kg, sac -- the order a shopkeeper counts in. */
    List<ProductPackaging> findByProductIdOrderByFactorAsc(Long productId);

    boolean existsByBarcode(String barcode);
}
