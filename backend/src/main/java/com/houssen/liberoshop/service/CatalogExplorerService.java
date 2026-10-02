package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.util.SearchText;
import com.houssen.liberoshop.web.dto.CatalogEntry;
import com.houssen.liberoshop.web.dto.CatalogPageResponse;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import com.houssen.liberoshop.web.dto.FavoritesResponse;
import com.houssen.liberoshop.web.dto.ProductPageResponse;
import com.houssen.liberoshop.web.dto.ProductResponse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.houssen.liberoshop.repository.ProductSpecifications.amongOrIn;
import static com.houssen.liberoshop.repository.ProductSpecifications.containsAllWords;
import static com.houssen.liberoshop.repository.ProductSpecifications.directlyIn;
import static com.houssen.liberoshop.repository.ProductSpecifications.inCategories;

/**
 * The catalogue as a file explorer: rayons are folders, products are the files in them.
 *
 * <p>Built on what the sale screens already use -- the rayon tree, the favourites, the product
 * {@link Specification}s and {@link ProductSearchService#page} -- and composed into one list of
 * {@link CatalogEntry}: the folders of the place being looked at, then a page of its products.
 *
 * <p>Where the grid is decides both halves:
 * <ul>
 *   <li>the top, with nothing typed: the main rayons, then the best sellers;</li>
 *   <li>inside a rayon: its sub-rayons, then the products filed directly in it -- or, asked for
 *       {@code all}, every product below it;</li>
 *   <li>the favourites: the pinned rayons as folders, then the pinned products;</li>
 *   <li>with words typed: the rayons whose name matches, then the matching products -- within the
 *       open rayon and everything below it, if one is open.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CatalogExplorerService {

    /** At most this many folders found by a search: enough to pick from, not to read. */
    static final int FOLDER_HITS = 12;

    private final ProductSearchService search;
    private final CategoryService categories;
    private final FavoriteService favorites;
    private final ProductRepository products;

    public CatalogExplorerService(ProductSearchService search, CategoryService categories,
                                  FavoriteService favorites, ProductRepository products) {
        this.search = search;
        this.categories = categories;
        this.favorites = favorites;
        this.products = products;
    }

    /**
     * @param rayonId     the open folder, or null for the top
     * @param all         inside a folder, every product below it rather than those filed in it
     * @param query       words typed; blank for none
     * @param favoritesOf the seller whose favourites to explore, or null
     * @param page        zero-based page of products; folders come with page 0 only
     * @param size        products per page
     */
    public CatalogPageResponse explore(Long rayonId, boolean all, String query, UserApp favoritesOf,
                                       int page, int size) {
        List<String> words = SearchText.words(query);
        boolean searching = !words.isEmpty();
        List<CategoryNodeResponse> tree = categories.tree();
        FavoritesResponse pinned = favoritesOf == null ? null : favorites.of(favoritesOf);

        List<CategoryNodeResponse> folders = page > 0 ? List.of()
                : foldersOf(rayonId, query, searching, pinned, tree);

        ProductPageResponse found;
        if (rayonId == null && pinned == null && !searching) {
            // The top of the shop: what sells, a single page.
            List<ProductResponse> featured = page > 0 ? List.of() : search.featured(size);
            found = new ProductPageResponse(featured, featured.size(), 0, size, false);
        } else {
            Specification<Product> where = containsAllWords(words);
            if (pinned != null) {
                where = where.and(amongOrIn(pinned.productIds(), List.of()));
            }
            if (rayonId != null) {
                where = where.and(all || searching
                        ? inCategories(categories.branchOf(rayonId))
                        : directlyIn(rayonId));
            }
            found = search.page(where, page, size);
        }

        List<CatalogEntry> entries = new ArrayList<>();
        Counts counts = folders.isEmpty() ? null : new Counts(tree, directCounts());
        folders.forEach(node -> entries.add(new CatalogEntry.Folder(node, node.children(), counts.inBranch(node.id()))));
        found.items().forEach(product -> entries.add(new CatalogEntry.Item(product)));
        return new CatalogPageResponse(List.copyOf(entries), found.total(), found.page(), found.hasMore());
    }

    private List<CategoryNodeResponse> foldersOf(Long rayonId, String query, boolean searching,
                                                 FavoritesResponse pinned, List<CategoryNodeResponse> tree) {
        if (searching) {
            Set<Long> within = rayonId == null ? Set.of() : categories.branchOf(rayonId);
            return categories.search(query, Integer.MAX_VALUE).stream()
                    .filter(node -> rayonId == null || (within.contains(node.id()) && !node.id().equals(rayonId)))
                    .limit(FOLDER_HITS)
                    .toList();
        }
        if (pinned != null) {
            Map<Long, CategoryNodeResponse> byId = tree.stream()
                    .collect(Collectors.toMap(CategoryNodeResponse::id, Function.identity()));
            return pinned.categoryIds().stream().map(byId::get).filter(node -> node != null).toList();
        }
        return tree.stream()
                .filter(node -> rayonId == null ? node.parentId() == null : rayonId.equals(node.parentId()))
                .toList();
    }

    private Map<Long, Long> directCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : products.countByCategory()) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * Products per folder, sub-folders included -- the Composite's sum, worked out on the tree in
     * memory from one grouped count rather than with a query per folder.
     */
    private static final class Counts {
        private final Map<Long, List<Long>> childrenOf = new HashMap<>();
        private final Map<Long, Long> direct;
        private final Map<Long, Long> memo = new HashMap<>();

        Counts(List<CategoryNodeResponse> tree, Map<Long, Long> direct) {
            this.direct = direct;
            tree.forEach(node -> {
                if (node.parentId() != null) {
                    childrenOf.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node.id());
                }
            });
        }

        long inBranch(Long id) {
            Long known = memo.get(id);
            if (known != null) {
                return known;
            }
            long total = direct.getOrDefault(id, 0L);
            for (Long child : childrenOf.getOrDefault(id, List.of())) {
                total += inBranch(child);
            }
            memo.put(id, total);
            return total;
        }
    }
}
