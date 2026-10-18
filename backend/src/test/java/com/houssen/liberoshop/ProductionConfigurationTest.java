package com.houssen.liberoshop;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.InvoiceNumbering;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The other tests run on an in-memory database that Hibernate creates from scratch. The
 * shop runs on a file that outlives every restart, with {@code ddl-auto=update} and the
 * startup migrations replayed on an existing schema. This test starts the real application
 * that way, twice on the same file, as a shop does every morning.
 */
class ProductionConfigurationTest {

    @TempDir
    Path folder;

    private ConfigurableApplicationContext start() {
        Path data = folder.resolve("data");
        return BackendApplication.start(new String[] {
                "--spring.datasource.url=jdbc:h2:file:" + data.resolve("libertyshop").toAbsolutePath(),
                "--spring.jpa.hibernate.ddl-auto=update",
                "--liberoshop.demo-data.enabled=false",
                "--server.port=0",
                "--liberoshop.security.jwt.secret-file=" + data.resolve("jwt-secret.key"),
                "--liberoshop.security.recovery.code-file=" + data.resolve("recovery-code.txt"),
                "--liberoshop.security.first-password-file=" + data.resolve("first-password.txt"),
                "--liberoshop.mobile.downloads-dir=" + folder.resolve("downloads"),
        });
    }

    @Test
    @DisplayName("base en fichier et ddl-auto=update : la boutique redemarre sur ses donnees, migrations rejouees sans erreur")
    void redemarrageSurLaBaseDeLaBoutique() {
        Long riz;
        String firstInvoice;
        try (ConfigurableApplicationContext first = start()) {
            riz = first.getBean(ProductRepository.class).save(Product.builder().name("Riz 5 kg")
                    .price(new BigDecimal("12500")).stockQuantity(qty(5)).build()).getId();
            firstInvoice = first.getBean(TransactionTemplate.class)
                    .execute(status -> first.getBean(InvoiceNumbering.class).next());
        }
        assertTrue(Files.exists(folder.resolve("data").resolve("libertyshop.mv.db")));

        try (ConfigurableApplicationContext second = start()) {
            Product found = second.getBean(ProductRepository.class).findById(riz).orElseThrow();
            assertEquals("Riz 5 kg", found.getName());
            assertEquals(0, new BigDecimal("12500").compareTo(found.getPrice()));
            String next = second.getBean(TransactionTemplate.class)
                    .execute(status -> second.getBean(InvoiceNumbering.class).next());
            assertEquals(Long.parseLong(firstInvoice.substring(2)) + 1, Long.parseLong(next.substring(2)),
                    "le compteur de factures reprend apres un redemarrage, sans doublon");
        }
    }
}
