package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.CategoryImage;
import com.houssen.liberoshop.service.CategoryImageService;
import com.houssen.liberoshop.service.CategoryImportService;
import com.houssen.liberoshop.service.CategoryService;
import com.houssen.liberoshop.service.ProductFilingService;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import com.houssen.liberoshop.web.dto.CreateCategoryRequest;
import com.houssen.liberoshop.web.dto.FileProductsRequest;
import com.houssen.liberoshop.web.dto.ImportPreviewRequest;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse;
import com.houssen.liberoshop.web.dto.SimpleImportRequest;
import com.houssen.liberoshop.web.dto.SimpleImportResultResponse;
import com.houssen.liberoshop.web.dto.UpdateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
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
    private final CategoryImportService categoryImportService;
    private final CategoryImageService categoryImageService;
    private final ProductFilingService filing;

    public CategoryController(CategoryService categoryService,
                              CategoryImportService categoryImportService,
                              CategoryImageService categoryImageService,
                              ProductFilingService filing) {
        this.categoryService = categoryService;
        this.categoryImportService = categoryImportService;
        this.categoryImageService = categoryImageService;
        this.filing = filing;
    }

    /** The whole tree, depth-first, each row carrying its depth -- see CategoryNodeResponse. */
    @GetMapping
    public List<CategoryNodeResponse> categories() {
        return categoryService.tree();
    }

    /** The rayons whose name holds every word of {@code q}: what a sale screen offers to open. */
    @GetMapping("/search")
    public List<CategoryNodeResponse> search(@RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "8") int limit) {
        return categoryService.search(q, limit);
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

    /** Products filed in this rayon: one changing rayon, or picked to join it. */
    @PutMapping("/{id}/products")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public FiledProducts file(@PathVariable Long id, @Valid @RequestBody FileProductsRequest request) {
        return new FiledProducts(filing.file(request.productIds(), id));
    }

    /** Every product of this rayon moved to {@code to}; the rayon stays, empty. */
    @PostMapping("/{id}/move-products")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public FiledProducts moveProducts(@PathVariable Long id, @RequestParam Long to) {
        return new FiledProducts(filing.moveAll(id, to));
    }

    /** @param moved how many products changed rayon */
    public record FiledProducts(int moved) {
    }

    /** @param productsMoved how many references changed rayon on the way out */
    public record DeletedCategory(Long id, int productsMoved) {
    }

    // ----------------------------------------------------------------------- image

    /**
     * The rayon's picture. Cached for good: its address carries the version (the screens add
     * {@code ?v=imageVersion}), so a new picture is a new address and never a stale one.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        CategoryImage image = categoryImageService.find(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .body(image.getContent());
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public CategoryNodeResponse uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file)
            throws IOException {
        return categoryImageService.store(id, file.getBytes());
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public CategoryNodeResponse removeImage(@PathVariable Long id) {
        return categoryImageService.remove(id);
    }

    // ---------------------------------------------------------------------- import

    /**
     * Reads a file of rayons and says what it would build. Writes nothing.
     *
     * <p>Two calls rather than one, like every other import here: a file that grows the tree the
     * wrong way is tedious to undo one rayon at a time, so it is shown first.
     */
    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public SimpleImportPreviewResponse previewImport(@Valid @RequestBody ImportPreviewRequest request) {
        return categoryImportService.preview(request.content());
    }

    @PostMapping("/import/apply")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public SimpleImportResultResponse applyImport(@Valid @RequestBody SimpleImportRequest request) {
        return categoryImportService.apply(request);
    }
}
