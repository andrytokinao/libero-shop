package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.CatalogService;
import com.houssen.liberoshop.web.dto.CategoryStockResponse;
import com.houssen.liberoshop.web.dto.ProductResponse;
import com.houssen.liberoshop.web.dto.SupplierResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The catalogue. Readable by every authenticated role: a cash desk needs prices, the depot
 * needs quantities, and the manager needs both.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    public List<ProductResponse> products(@RequestParam(required = false) String search,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(defaultValue = "false") boolean lowStockOnly) {
        return catalogService.findProducts(search, categoryId, lowStockOnly);
    }

    // Categories are served by CategoryController: they are a tree now, with writes beside the
    // read, and two handlers on /api/categories would not even start.

    @GetMapping("/suppliers")
    public List<SupplierResponse> suppliers() {
        return catalogService.findSuppliers();
    }

    @GetMapping("/stock/by-category")
    public List<CategoryStockResponse> stockByCategory() {
        return catalogService.stockByCategory();
    }
}
