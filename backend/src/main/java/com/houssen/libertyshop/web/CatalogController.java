package com.houssen.libertyshop.web;

import com.houssen.libertyshop.service.CatalogService;
import com.houssen.libertyshop.web.dto.CategoryResponse;
import com.houssen.libertyshop.web.dto.CategoryStockResponse;
import com.houssen.libertyshop.web.dto.ProductResponse;
import com.houssen.libertyshop.web.dto.SupplierResponse;
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

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.findCategories();
    }

    @GetMapping("/suppliers")
    public List<SupplierResponse> suppliers() {
        return catalogService.findSuppliers();
    }

    @GetMapping("/stock/by-category")
    public List<CategoryStockResponse> stockByCategory() {
        return catalogService.stockByCategory();
    }
}
