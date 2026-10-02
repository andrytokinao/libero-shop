package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.CategoryImage;
import com.houssen.liberoshop.repository.CategoryImageRepository;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * The pictures of the rayons -- what the sale screens' folder tiles show instead of a generic
 * icon, so a cashier recognises "Boissons" by its bottles before reading it.
 *
 * <p>Built like the profile photos ({@link UserPhotoService}): the bytes apart, a version on the
 * rayon that is part of the picture's address, the same checks ({@link ImageUpload}).
 */
@Service
@Transactional(readOnly = true)
public class CategoryImageService {

    private final CategoryImageRepository images;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final Clock clock;

    public CategoryImageService(CategoryImageRepository images, CategoryRepository categories,
                                CategoryService categoryService, Clock clock) {
        this.images = images;
        this.categories = categories;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    public CategoryImage find(Long categoryId) {
        return images.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune image pour ce rayon."));
    }

    /** Replaces the rayon's picture, and answers the rayon with its new {@code imageVersion}. */
    @Transactional
    public CategoryNodeResponse store(Long categoryId, byte[] content) {
        Category category = require(categoryId);
        PhotoFormat format = ImageUpload.check(content, CategoryImage.MAX_BYTES, "L'image");
        CategoryImage image = images.findById(categoryId)
                .orElseGet(() -> CategoryImage.builder().category(category).build());
        image.setContent(content);
        image.setContentType(format.contentType());
        images.save(image);
        category.setImageVersion(clock.millis());
        return categoryService.node(categoryId);
    }

    /** Back to the initial. Nothing to do when there was no picture. */
    @Transactional
    public CategoryNodeResponse remove(Long categoryId) {
        Category category = require(categoryId);
        images.findById(categoryId).ifPresent(images::delete);
        category.setImageVersion(null);
        return categoryService.node(categoryId);
    }

    private Category require(Long id) {
        return categories.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Categorie", id));
    }
}
