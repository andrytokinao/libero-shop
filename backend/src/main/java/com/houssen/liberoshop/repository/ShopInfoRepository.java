package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.ShopInfo;
import org.springframework.data.jpa.repository.JpaRepository;

/** The shop's identity: a single row, read by {@link ShopInfo#SINGLETON_ID}. */
public interface ShopInfoRepository extends JpaRepository<ShopInfo, Long> {
}