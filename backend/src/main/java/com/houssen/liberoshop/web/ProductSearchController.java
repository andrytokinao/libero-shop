package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.CatalogExplorerService;
import com.houssen.liberoshop.service.ProductSearchService;
import com.houssen.liberoshop.web.dto.CatalogPageResponse;
import com.houssen.liberoshop.web.dto.ProductPageResponse;
import com.houssen.liberoshop.web.dto.ProductResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Finding a product to sell. Open to every authenticated role, like the rest of the catalogue:
 * whoever can take a sale needs it.
 */
@RestController
@RequestMapping("/api/products")
public class ProductSearchController {

    private final ProductSearchService searchService;
    private final CatalogExplorerService explorer;
    private final CurrentUser currentUser;

    public ProductSearchController(ProductSearchService searchService, CatalogExplorerService explorer,
                                   CurrentUser currentUser) {
        this.searchService = searchService;
        this.explorer = explorer;
        this.currentUser = currentUser;
    }

    /**
     * The product a scanned or typed code designates, 404 when none does. A query parameter
     * rather than a path segment: a shop's own codes may carry a slash.
     */
    @GetMapping("/lookup")
    public ProductResponse lookup(@RequestParam String code) {
        return searchService.findByCode(code);
    }

    /** At most {@code limit} products matching every word of {@code q}, within a rayon if given. */
    @GetMapping("/search")
    public List<ProductResponse> search(@RequestParam(defaultValue = "") String q,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(defaultValue = "" + ProductSearchService.DEFAULT_LIMIT) int limit) {
        return searchService.search(q, categoryId, limit);
    }

    /**
     * One page of what a sale screen is browsing: words, a rayon with everything below it, the
     * signed-in seller's favourites -- any combination -- by name, with the total for "Afficher
     * plus". What the screens use; {@code /search} and {@code /featured} stay for the mobile
     * bundles installed before it.
     */
    @GetMapping("/browse")
    public ProductPageResponse browse(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(defaultValue = "false") boolean favorites,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "" + ProductSearchService.DEFAULT_LIMIT) int size) {
        return searchService.browse(q, categoryId, favorites ? currentUser.require() : null, page, size);
    }

    /**
     * The catalogue as a file explorer: the folders (rayons) of where the grid is, then a page of
     * its products -- see {@link CatalogExplorerService}. Under {@code /api/products} with the
     * other reads of the sale screens.
     *
     * @param rayonId the open folder, or none for the top
     * @param all     in a folder, every product below it rather than those filed directly in it
     */
    @GetMapping("/explore")
    public CatalogPageResponse explore(@RequestParam(required = false) Long rayonId,
                                       @RequestParam(defaultValue = "false") boolean all,
                                       @RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "false") boolean favorites,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "" + ProductSearchService.DEFAULT_LIMIT) int size) {
        return explorer.explore(rayonId, all, q, favorites ? currentUser.require() : null, page, size);
    }

    /** What to show before anything is typed: the best sellers, topped up by name. */
    @GetMapping("/featured")
    public List<ProductResponse> featured(
            @RequestParam(defaultValue = "" + ProductSearchService.DEFAULT_LIMIT) int limit) {
        return searchService.featured(limit);
    }
}
