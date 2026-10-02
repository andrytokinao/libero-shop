package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.repository.CategoryImageRepository;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A rayon's picture: checked like a profile photo, versioned, and gone with its rayon. */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:category-image;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryImageServiceTest {

    /** The first bytes of a PNG: enough for the format check, which reads nothing else. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    @Autowired
    private TestEntityManager db;
    @Autowired
    private CategoryImageRepository images;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ProductRepository products;

    private CategoryImageService service;
    private Category boissons;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneId.systemDefault());
        service = new CategoryImageService(images, categories, new CategoryService(categories, products), clock);
        boissons = db.persist(Category.builder().name("Boissons").build());
        db.flush();
    }

    @Test
    @DisplayName("a picture is stored with its type, and the rayon carries its version")
    void stores() {
        CategoryNodeResponse rayon = service.store(boissons.getId(), PNG);

        assertEquals(1_700_000_000_000L, rayon.imageVersion());
        assertEquals("image/png", service.find(boissons.getId()).getContentType());
        assertArrayEquals(PNG, service.find(boissons.getId()).getContent());
    }

    @Test
    @DisplayName("a second picture replaces the first")
    void replaces() {
        service.store(boissons.getId(), PNG);
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};

        service.store(boissons.getId(), jpeg);

        assertEquals("image/jpeg", service.find(boissons.getId()).getContentType());
        assertEquals(1, images.count());
    }

    @Test
    @DisplayName("what is not an image, or too heavy, is refused with the profile photo's codes")
    void refusesBadFiles() {
        assertEquals("PHOTO_FORMAT", assertThrows(BusinessRuleException.class,
                () -> service.store(boissons.getId(), "pas une image".getBytes())).code());
        assertEquals("PHOTO_EMPTY", assertThrows(BusinessRuleException.class,
                () -> service.store(boissons.getId(), new byte[0])).code());
        byte[] huge = new byte[600 * 1024];
        System.arraycopy(PNG, 0, huge, 0, PNG.length);
        assertEquals("PHOTO_TOO_LARGE", assertThrows(BusinessRuleException.class,
                () -> service.store(boissons.getId(), huge)).code());
    }

    @Test
    @DisplayName("removing the picture brings the rayon back to its initial")
    void removes() {
        service.store(boissons.getId(), PNG);

        assertNull(service.remove(boissons.getId()).imageVersion());
        assertThrows(ResourceNotFoundException.class, () -> service.find(boissons.getId()));
    }
}
