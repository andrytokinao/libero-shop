package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse.ImportFieldSpec;
import com.houssen.liberoshop.web.dto.SimpleImportRequest;
import com.houssen.liberoshop.web.dto.SimpleImportResultResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loading the shop's wholesalers from a file.
 *
 * <p>This one closes a real hole rather than saving typing. Until now suppliers only ever came
 * from {@code DataInitializer}: an installation that turned the starter dataset off had none at
 * all and no screen to add one -- which also meant the depot could not book a single receipt.
 * A file of contacts is how a shop actually holds this list today, in a notebook or a
 * spreadsheet, so reading that file is the shortest route from installed to usable.
 *
 * <p>A name already on the list is completed, never overwritten: a blank contact is filled in
 * from the file, one the shop has already typed stands. The shop's own record of who it buys
 * from is more trustworthy than an export, and silently replacing a phone number somebody
 * corrected by hand would be the worst thing this could do.
 */
@Service
@Transactional(readOnly = true)
public class SupplierImportService {

    public static final String FIELD_NAME = "nom";
    public static final String FIELD_CONTACT = "contact";
    public static final String FIELD_PRODUCTS = "produits fournis";

    /** Long enough for "034 12 345 67 / commandes@grossiste.mg", short enough to stay a contact. */
    private static final int MAX_CONTACT = 120;
    private static final int MAX_NAME = 120;
    private static final int MAX_PRODUCTS = 255;

    private static final String[] NAME_ALIASES = {
            "nom", "fournisseur", "nomfournisseur", "raisonsociale", "societe", "supplier", "name"
    };
    private static final String[] CONTACT_ALIASES = {
            "contact", "telephone", "tel", "gsm", "portable", "email", "mail", "coordonnees",
            "phone", "numero"
    };
    private static final String[] PRODUCTS_ALIASES = {
            "produitsfournis", "produits", "articles", "fournitures", "specialite",
            "categorie", "livre", "suppliedproducts"
    };

    private final SupplierRepository suppliers;

    public SupplierImportService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    public static List<ImportFieldSpec> fields() {
        return List.of(
                new ImportFieldSpec(FIELD_NAME, "nom", true, MAX_NAME),
                new ImportFieldSpec(FIELD_CONTACT, "contact", false, MAX_CONTACT),
                new ImportFieldSpec(FIELD_PRODUCTS, "produits fournis", false, MAX_PRODUCTS));
    }

    // --------------------------------------------------------------------- preview

    public SimpleImportPreviewResponse preview(String content) {
        CsvTable table = CsvTable.parse(content);
        int nameAt = table.indexOf(NAME_ALIASES);
        int contactAt = table.indexOf(CONTACT_ALIASES);
        int productsAt = table.indexOf(PRODUCTS_ALIASES);
        if (nameAt < 0) {
            throw new BusinessRuleException("IMPORT_NO_NAME_COLUMN",
                    "Aucune colonne de nom de fournisseur n'a ete reconnue. Nommez-la \"nom\" "
                            + "(ou \"fournisseur\", \"raison sociale\"). En-tetes lus : "
                            + String.join(", ", table.headers()));
        }

        Map<String, Supplier> known = new LinkedHashMap<>();
        suppliers.findAllByOrderByNameAsc()
                .forEach(supplier -> known.putIfAbsent(CsvTable.normalise(supplier.getName()), supplier));
        // Names this file brings in, so a second line naming the same wholesaler is caught too.
        Map<String, Integer> seen = new LinkedHashMap<>();

        List<SimpleImportPreviewResponse.Line> lines = new ArrayList<>();
        int created = 0;
        int merged = 0;
        int skipped = 0;

        for (CsvTable.Row row : table.rows()) {
            String name = row.cell(nameAt).replaceAll("\\s+", " ").trim();
            String contact = capped(contactAt < 0 ? "" : row.cell(contactAt), MAX_CONTACT);
            String products = capped(productsAt < 0 ? "" : row.cell(productsAt), MAX_PRODUCTS);
            List<String> notes = new ArrayList<>();

            if (name.isEmpty()) {
                skipped++;
                lines.add(line(row, "", contact, products, ImportOutcome.SKIPPED,
                        ImportAction.CREATE, null, null, false,
                        List.of("Ligne sans nom de fournisseur : rien a enregistrer.")));
                continue;
            }

            String folded = CsvTable.normalise(name);
            Integer twin = seen.get(folded);
            if (twin != null) {
                skipped++;
                lines.add(line(row, name, contact, products, ImportOutcome.SKIPPED,
                        ImportAction.CREATE, null, null, false,
                        List.of("Doublon dans le fichier : deja lu ligne " + twin + ".")));
                continue;
            }
            seen.put(folded, row.line());

            Supplier existing = known.get(folded);
            if (existing != null) {
                merged++;
                notes.add(describeMerge(existing, contact, products));
                lines.add(line(row, name, contact, products, ImportOutcome.MERGED,
                        ImportAction.MERGE, existing.getId(), existing.getName(), true, notes));
                continue;
            }

            if (contact.isEmpty()) {
                notes.add("Aucun contact dans le fichier : a saisir ici, sinon le depot n'aura "
                        + "aucun moyen de joindre ce fournisseur.");
            }
            created++;
            lines.add(line(row, name, contact, products, ImportOutcome.CREATED,
                    ImportAction.CREATE, null, null, true, notes));
        }

        List<String> recognised = new ArrayList<>();
        recognised.add("nom : " + table.headers().get(nameAt).trim());
        List<String> missing = new ArrayList<>();
        if (contactAt >= 0) {
            recognised.add("contact : " + table.headers().get(contactAt).trim());
        } else {
            missing.add("contact");
        }
        if (productsAt >= 0) {
            recognised.add("produits fournis : " + table.headers().get(productsAt).trim());
        } else {
            missing.add("produits fournis");
        }
        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < table.headers().size(); i++) {
            String header = table.headers().get(i).trim();
            if (!header.isEmpty() && i != nameAt && i != contactAt && i != productsAt) {
                ignored.add(header);
            }
        }

        return new SimpleImportPreviewResponse("suppliers", String.valueOf(table.separator()),
                lines.size(), created, merged, skipped, fields(), recognised, ignored,
                List.copyOf(missing), List.copyOf(lines));
    }

    /** Says in advance exactly what a merge would and would not touch. */
    private static String describeMerge(Supplier existing, String contact, String products) {
        List<String> fills = new ArrayList<>();
        List<String> keeps = new ArrayList<>();
        if (!contact.isEmpty()) {
            (isBlank(existing.getContact()) ? fills : keeps).add("contact");
        }
        if (!products.isEmpty()) {
            (isBlank(existing.getSuppliedProducts()) ? fills : keeps).add("produits fournis");
        }
        if (fills.isEmpty() && keeps.isEmpty()) {
            return "Ce fournisseur est deja enregistre : rien a completer.";
        }
        StringBuilder said = new StringBuilder("Fournisseur deja enregistre.");
        if (!fills.isEmpty()) {
            said.append(" Sera complete : ").append(String.join(", ", fills)).append(".");
        }
        if (!keeps.isEmpty()) {
            said.append(" Conserve (la fiche de la boutique fait foi) : ")
                    .append(String.join(", ", keeps)).append(".");
        }
        return said.toString();
    }

    private static SimpleImportPreviewResponse.Line line(CsvTable.Row row, String name,
                                                         String contact, String products,
                                                         ImportOutcome outcome, ImportAction action,
                                                         Long existingId, String existingLabel,
                                                         boolean selected, List<String> notes) {
        return new SimpleImportPreviewResponse.Line(row.line(),
                Map.of(FIELD_NAME, name, FIELD_CONTACT, contact, FIELD_PRODUCTS, products),
                outcome, action, existingId, existingLabel, selected, List.copyOf(notes));
    }

    // ----------------------------------------------------------------------- apply

    @RequiresActiveLicense
    @Transactional
    public SimpleImportResultResponse apply(SimpleImportRequest request) {
        Map<String, Supplier> known = new LinkedHashMap<>();
        suppliers.findAllByOrderByNameAsc()
                .forEach(supplier -> known.putIfAbsent(CsvTable.normalise(supplier.getName()), supplier));

        List<SimpleImportResultResponse.Line> done = new ArrayList<>();
        int created = 0;
        int merged = 0;
        int skipped = 0;

        for (SimpleImportRequest.Line line : request.lines()) {
            String name = value(line, FIELD_NAME).replaceAll("\\s+", " ");
            String contact = capped(value(line, FIELD_CONTACT), MAX_CONTACT);
            String products = capped(value(line, FIELD_PRODUCTS), MAX_PRODUCTS);

            if (name.isEmpty()) {
                skipped++;
                done.add(new SimpleImportResultResponse.Line(line.line(), "",
                        ImportOutcome.SKIPPED, null, "Ligne sans nom de fournisseur."));
                continue;
            }

            // Matched again here, and by name rather than by the id the preview sent: a colleague
            // may have created this very wholesaler in between, and two rows for one supplier is
            // exactly what this import exists to avoid.
            Supplier existing = known.get(CsvTable.normalise(name));
            if (existing != null) {
                List<String> filled = new ArrayList<>();
                if (isBlank(existing.getContact()) && !contact.isEmpty()) {
                    existing.setContact(contact);
                    filled.add("contact");
                }
                if (isBlank(existing.getSuppliedProducts()) && !products.isEmpty()) {
                    existing.setSuppliedProducts(products);
                    filled.add("produits fournis");
                }
                merged++;
                done.add(new SimpleImportResultResponse.Line(line.line(), existing.getName(),
                        ImportOutcome.MERGED, existing.getId(),
                        filled.isEmpty() ? "Deja enregistre, rien a completer."
                                : "Complete : " + String.join(", ", filled) + "."));
                continue;
            }

            Supplier saved = suppliers.save(Supplier.builder()
                    .name(capped(name, MAX_NAME))
                    .contact(contact.isEmpty() ? null : contact)
                    .suppliedProducts(products.isEmpty() ? null : products)
                    .build());
            known.put(CsvTable.normalise(saved.getName()), saved);
            created++;
            done.add(new SimpleImportResultResponse.Line(line.line(), saved.getName(),
                    ImportOutcome.CREATED, saved.getId(), ""));
        }

        return new SimpleImportResultResponse(created, merged, skipped, List.copyOf(done));
    }

    // ----------------------------------------------------------------- small stuff

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Trimmed, and cut to what the column holds rather than letting the insert fail. */
    private static String capped(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max).trim();
    }

    private static String value(SimpleImportRequest.Line line, String key) {
        return Optional.ofNullable(line.values().get(key)).orElse("").trim();
    }
}
