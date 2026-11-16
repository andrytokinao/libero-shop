package com.houssen.liberoshop.printer.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Fills a Thymeleaf template and turns the result into a PDF: the one place where the shop's
 * documents (invoices, receipts) become files, so each of them only builds its model.
 *
 * <p>The template must be well-formed XHTML and plain CSS 2.1 -- see the header comment of
 * {@code templates/pdf/invoice.html}. The standard PDF fonts (Helvetica) draw the French accents;
 * a product name in another alphabet needs a font embedded with {@code useFont(...)}.
 */
@Component
public class HtmlPdfRenderer {

    private final TemplateEngine templates;

    public HtmlPdfRenderer(TemplateEngine templates) {
        this.templates = templates;
    }

    /**
     * @param template path under {@code templates/}, without extension: {@code "pdf/invoice"}
     * @param variable the name the template reads the model under
     * @param model    what the template displays
     */
    public byte[] render(String template, String variable, Object model) {
        Context context = new Context(Locale.FRENCH);
        context.setVariable(variable, model);
        String html = templates.process(template, context);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Le document " + template + " n'a pas pu etre mis en PDF", e);
        } catch (Exception e) {
            // PdfRendererBuilder.run() declares Exception: a malformed template ends here.
            throw new IllegalStateException("Le gabarit " + template + " est invalide", e);
        }
    }
}