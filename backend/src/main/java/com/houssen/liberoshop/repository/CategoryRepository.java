package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    List<Category> findAllByOrderByNameAsc();

    /**
     * The whole tree in one query, parents included.
     *
     * <p>Every category screen and the product import need the ancestors of each row -- to
     * indent it, to print "Boissons &rsaquo; Eau", to refuse a cycle. Reading them through
     * the lazy association would be one query per level per row; there are a few dozen
     * categories in a grocery, so fetching the lot and walking it in memory is both cheaper
     * and the only way {@code open-in-view=false} lets a response be built outside the
     * service.
     */
    @Query("select c from Category c left join fetch c.parent order by c.name")
    List<Category> findAllWithParent();

    long countByParentId(Long parentId);
}
