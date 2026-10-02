package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** The products one account pinned, newest first. */
    @Query("select f.product.id from Favorite f where f.user.id = :userId and f.product is not null "
            + "order by f.pinnedAt desc")
    List<Long> findPinnedProductIds(@Param("userId") Long userId);

    /** The rayons one account pinned, newest first. */
    @Query("select f.category.id from Favorite f where f.user.id = :userId and f.category is not null "
            + "order by f.pinnedAt desc")
    List<Long> findPinnedCategoryIds(@Param("userId") Long userId);

    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);

    Optional<Favorite> findByUserIdAndCategoryId(Long userId, Long categoryId);
}
