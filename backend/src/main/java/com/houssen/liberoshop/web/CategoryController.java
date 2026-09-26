package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.CategoryService;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import com.houssen.liberoshop.web.dto.CreateCategoryRequest;
import com.houssen.liberoshop.web.dto.UpdateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The rayons of the shop.
 *
 * <p>Read by everyone -- a cash desk filters its product search by rayon -- and written by the
 * two roles that own the catalogue: the depot manager, who organises the stock day to day, and
 * the super-admin. A cashier reorganising the rayons mid-shift is not a thing anyone asked for.
 *
 * <p>{@code GET} lives here rather than on {@code CatalogController} because the tree is the
 * only thing this resource has ever returned flat, and the write endpoints beside it are what
 * make that shape make sense.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** The whole tree, depth-first, each row carrying its depth -- see CategoryNodeResponse. */
    @GetMapping
    public List<CategoryNodeResponse> categories() {
        return categoryService.tree();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public CategoryNodeResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.create(request);
    }

    /** Renames, and moves when {@code parentId} differs -- including to null, meaning a root. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public CategoryNodeResponse update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(id, request);
    }

    /**
     * Removes a rayon. A rayon still holding goods is refused unless the caller says where they
     * go, which is why this takes a parameter at all rather than being a bare delete.
     *
     * @param moveProductsTo the rayon that receives the goods; omit it to be told the count
     *                       instead, which is what the screen does before asking the operator
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public DeletedCategory delete(@PathVariable Long id,
                                  @RequestParam(required = false) Long moveProductsTo) {
        return new DeletedCategory(id, categoryService.delete(id, moveProductsTo));
    }

    /** @param productsMoved how many references changed rayon on the way out */
    public record DeletedCategory(Long id, int productsMoved) {
    }
}
