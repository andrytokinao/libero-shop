package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.ProductSearchService;
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

    public ProductSearchController(ProductSearchService searchService) {
        this.searchService = searchService;
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

    /** What to show before anything is typed: the best sellers, topped up by name. */
    @GetMapping("/featured")
    public List<ProductResponse> featured(
            @RequestParam(defaultValue = "" + ProductSearchService.DEFAULT_LIMIT) int limit) {
        return searchService.featured(limit);
    }
}
