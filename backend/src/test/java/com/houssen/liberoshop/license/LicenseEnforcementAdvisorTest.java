package com.houssen.liberoshop.license;

import com.houssen.liberoshop.license.exception.LicenseExpiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks that {@link RequiresActiveLicense} really degrades the application rather than
 * just documenting an intention: reads keep working, writes stop.
 */
class LicenseEnforcementAdvisorTest {

    private static final LocalDate EXPIRY = LocalDate.of(2027, 1, 1);

    @TempDir
    Path installDirectory;

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("lets annotated writes through while the license is active")
    void allowsWritesWhenActive() throws IOException {
        SaleService sales = salesAt(EXPIRY.minusDays(30));

        assertEquals("sale recorded", sales.recordSale());
        assertEquals("sales listed", sales.listSales());
    }

    @Test
    @DisplayName("blocks annotated writes once read-only, but not reads")
    void blocksWritesWhenReadOnly() throws IOException {
        SaleService sales = salesAt(EXPIRY.plusDays(20));

        LicenseExpiredException failure = assertThrows(LicenseExpiredException.class, sales::recordSale);
        assertEquals("LICENSE_EXPIRED", failure.code());

        // The whole point of degrading instead of blocking: the shop can still consult.
        assertEquals("sales listed", sales.listSales());
    }

    @Test
    @DisplayName("blocks every method of an annotated class")
    void blocksClassLevelAnnotation() throws IOException {
        context = contextAt(EXPIRY.plusDays(20));
        StockService stock = context.getBean(StockService.class);

        assertThrows(LicenseExpiredException.class, stock::recordMovement);
        assertThrows(LicenseExpiredException.class, stock::adjust);
    }

    private SaleService salesAt(LocalDate today) throws IOException {
        context = contextAt(today);
        return context.getBean(SaleService.class);
    }

    private AnnotationConfigApplicationContext contextAt(LocalDate today) throws IOException {
        KeyPair publisherKeys = TestLicenses.generateKeyPair();
        Path licensePath = installDirectory.resolve("libero-shop.lic");
        Files.write(licensePath, TestLicenses.license(publisherKeys)
                .expiresOn(EXPIRY)
                .graceDays(15)
                .build());

        AnnotationConfigApplicationContext created = new AnnotationConfigApplicationContext();
        created.registerBean(LicenseService.class, () -> new LicenseService(
                new LicenseVerifier(publisherKeys.getPublic()),
                TestLicenses.properties(licensePath),
                Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
                TestLicenses.DEFAULT_FINGERPRINT));
        created.register(EnforcementTestConfiguration.class);
        created.refresh();
        return created;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class EnforcementTestConfiguration {

        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        LicenseEnforcementAdvisor advisor(ObjectProvider<LicenseService> licenseService) {
            // Same lazy wiring as production, so the test covers the real lookup path.
            return new LicenseEnforcementAdvisor(licenseService::getObject);
        }

        @Bean
        SaleService saleService() {
            return new SaleService();
        }

        @Bean
        StockService stockService() {
            return new StockService();
        }
    }

    /** Stands in for the real sale service: one write method, one read method. */
    static class SaleService {

        @RequiresActiveLicense
        String recordSale() {
            return "sale recorded";
        }

        String listSales() {
            return "sales listed";
        }
    }

    /** A service where every method writes, so the annotation sits on the class. */
    @RequiresActiveLicense
    static class StockService {

        String recordMovement() {
            return "movement recorded";
        }

        String adjust() {
            return "stock adjusted";
        }
    }
}
