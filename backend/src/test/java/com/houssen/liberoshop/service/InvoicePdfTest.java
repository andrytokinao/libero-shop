package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The invoice as a PDF: what the customer reads on it, and who may download it. */
@SpringBootTest
@AutoConfigureMockMvc
class InvoicePdfTest {

    @Autowired
    private InvoicePdfService pdfs;
    @Autowired
    private SaleService sales;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private MockMvc mvc;

    private UserApp cashier() {
        String handle = "c" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Caisse " + handle).username(handle)
                .password("x").enabled(true).roles(EnumSet.of(RoleApp.CASHIER)).build());
    }

    private Product product(String name, String price) {
        return products.save(Product.builder().name(name).price(new BigDecimal(price)).stockQuantity(qty(100)).build());
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    @DisplayName("la facture en PDF porte son numero, le client, chaque ligne et le total en ariary")
    void factureEnPdf() throws Exception {
        UserApp cashier = cashier();
        Product riz = product("Riz étuvé 5 kg", "12500");
        Product huile = product("Huile 1 L", "8250.50");
        InvoiceResponse invoice = sales.checkout(new CreateSaleRequest("Rina", PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(riz.getId(), qty(2)),
                        new CreateSaleRequest.Line(huile.getId(), qty(1)))), cashier);

        InvoicePdfService.InvoicePdf pdf = pdfs.pdfFor(invoice.id(), cashier);
        String text = textOf(pdf.content());

        assertEquals(invoice.invoiceNumber(), pdf.invoiceNumber());
        assertTrue(text.contains("Facture " + invoice.invoiceNumber()), text);
        assertTrue(text.contains("Client : Rina"), text);
        assertTrue(text.contains("Riz étuvé 5 kg"), text);
        assertTrue(text.contains("25 000 Ar"), text);
        assertTrue(text.contains("8 250,50 Ar"), text);
        assertTrue(text.contains("33 250,50 Ar"), text);
        assertTrue(text.contains("Payée"), text);
    }

    @Test
    @DisplayName("une longue commande continue sur une deuxieme page")
    void longueCommandeSurPlusieursPages() throws Exception {
        UserApp cashier = cashier();
        List<CreateSaleRequest.Line> lines = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            lines.add(new CreateSaleRequest.Line(product("Article " + i + " " + UUID.randomUUID(), "100").getId(), qty(1)));
        }
        InvoiceResponse invoice = sales.checkout(
                new CreateSaleRequest(null, PaymentStatus.UNPAID, PaymentMethod.CASH, lines), cashier);

        byte[] pdf = pdfs.pdfFor(invoice.id(), cashier).content();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 2);
        }
        assertTrue(textOf(pdf).contains("À payer"));
    }

    @Test
    @DisplayName("un caractere que la police ne sait pas dessiner devient ?, la facture sort quand meme")
    void caractereInconnuRemplace() {
        PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        assertEquals("Café ? 250g", InvoicePdfWriter.drawable(helvetica, "Café ☕ 250g"));
    }

    @Test
    @DisplayName("montants : espaces entre les milliers, centimes seulement s'il y en a")
    void montants() {
        assertEquals("12 500 Ar", InvoicePdfWriter.amount(new BigDecimal("12500.00")));
        assertEquals("1 250,50 Ar", InvoicePdfWriter.amount(new BigDecimal("1250.5")));
    }

    @Test
    @DisplayName("sans connexion, pas de PDF")
    void sansConnexionRefuse() throws Exception {
        mvc.perform(get("/api/invoices/1/pdf")).andExpect(status().isUnauthorized());
    }
}
