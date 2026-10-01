package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rows written by a version that had no {@code search_text} -- inserted here in plain SQL, past
 * the entity callbacks, the way an upgraded installation's rows look -- get one at start-up.
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:backfill;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SearchTextBackfillTest {

    @Autowired
    private TestEntityManager db;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;

    @Test
    @DisplayName("fills what an older version left empty, then has nothing left to do")
    void fillsLegacyRows() {
        jdbc.update("insert into category (name) values ('Épicerie')");
        jdbc.update("insert into product (name, price, stock_quantity, barcode) "
                + "values ('Café Malagasy', 3500, 4, '611')");

        new SearchTextBackfill(products, categories).run(null);
        db.flush();
        db.clear();

        assertEquals("cafe malagasy 611", products.findAll().getFirst().getSearchText());
        assertEquals("epicerie", categories.findAll().getFirst().getSearchText());
        assertTrue(products.findBySearchTextIsNull().isEmpty());
        assertTrue(categories.findBySearchTextIsNull().isEmpty());
    }
}
