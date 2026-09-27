package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import com.houssen.liberoshop.web.dto.CreateCategoryRequest;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse.ImportFieldSpec;
import com.houssen.liberoshop.web.dto.SimpleImportRequest;
import com.houssen.liberoshop.web.dto.SimpleImportResultResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Building the rayon tree from a file.
 *
 * <p>The product import already creates the rayons its own file names, so this exists for the
 * case where the tree comes first: a shop that wants "Boissons &gt; Eau &gt; Eau gazeuse" laid
 * out before anything is filed into it, or one copying the organisation of a sister shop. It is
 * also the only way to give a rayon a parent in bulk.
 *
 * <p>A file may say it either way. One column of full paths -- {@code Boissons > Eau} -- or two
 * columns, the rayon and its parent. Both are read, and when both are present the explicit
 * parent column wins, because a file that bothers to have one means it.
 *
 * <p>One rule governs the rest: a rayon's parent must either already exist, or be a line of the
 * same file. Nothing is invented. A file naming a parent nobody defines has that line refused
 * with the parent's name, rather than quietly promoting the child to the top level -- which is
 * the failure that looks like success, because the rayon does appear, just in the wrong place.
 * The preview says so before applying, so the two never disagree.
 */
@Service
@Transactional(readOnly = true)
public class CategoryImportService {

    /** The rayon itself. A path here creates every level of it. */
    public static final String FIELD_NAME = "categorie";
    /** Where it hangs. Optional: blank makes a root. */
    public static final String FIELD_PARENT = "parent";

    private static final String[] NAME_ALIASES = {
            "categorie", "rayon", "nom", "famille", "category", "libelle", "chemin", "path"
    };
    private static final String[] PARENT_ALIASES = {
            "parent", "categorieparente", "rayonparent", "sousde", "famillemere",
            "parentcategory", "appartienta"
    };

    private final CategoryRepository categories;
    private final CategoryService categoryService;

    public CategoryImportService(CategoryRepository categories, CategoryService categoryService) {
        this.categories = categories;
        this.categoryService = categoryService;
    }

    /** The columns the dialog should render, and what it recommends calling them. */
    public static List<ImportFieldSpec> fields() {
        return List.of(
                new ImportFieldSpec(FIELD_NAME, "categorie", true, 60),
                new ImportFieldSpec(FIELD_PARENT, "parent", false, 60));
    }

    // --------------------------------------------------------------------- preview

    public SimpleImportPreviewResponse preview(String content) {
        CsvTable table = CsvTable.parse(content);
        int nameAt = table.indexOf(NAME_ALIASES);
        int parentAt = table.indexOf(PARENT_ALIASES);
        if (nameAt < 0) {
            throw new BusinessRuleException("IMPORT_NO_NAME_COLUMN",
                    "Aucune colonne de categorie n'a ete reconnue. Nommez-la \"categorie\" "
                            + "(ou \"rayon\", \"famille\"). En-tetes lus : "
                            + String.join(", ", table.headers()));
        }

        // What the tree will hold once this file is applied, folded for comparison. Seeded with
        // what exists, then grown line by line -- which is what makes a file that creates a
        // parent on line 2 and its child on line 5 work.
        Set<String> known = new LinkedHashSet<>();
        Map<String, Long> idOf = new LinkedHashMap<>();
        for (Category existing : categories.findAllWithParent()) {
            known.add(CsvTable.normalise(existing.getName()));
            idOf.put(CsvTable.normalise(existing.getName()), existing.getId());
        }

        // Read first, judge second. Whether a line's parent is satisfied depends on every other
        // line of the file, including ones further down -- so nothing can be settled in one pass.
        record Draft(CsvTable.Row row, String name, String parent, List<String> notes) {
        }
        List<Draft> drafts = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            String rawParent = parentAt < 0 ? "" : row.cell(parentAt);

            // "Boissons > Eau" in the name column: the last segment is the rayon, the one before
            // it is its parent -- unless a parent column already said so.
            List<String> segments = CategoryService.segmentsOf(row.cell(nameAt));
            String name = segments.isEmpty() ? "" : segments.getLast();
            String parent = !rawParent.isBlank() ? rawParent.trim()
                    : segments.size() > 1 ? segments.get(segments.size() - 2) : "";

            List<String> notes = new ArrayList<>();
            if (segments.size() > 2 && rawParent.isBlank()) {
                notes.add("Seul \"" + segments.get(segments.size() - 2) + "\" est lu comme parent ; "
                        + "les niveaux au-dessus doivent exister ou figurer dans ce fichier.");
            }
            drafts.add(new Draft(row, name, parent, notes));
        }

        // Everything the tree will hold once this file is applied: what exists, plus what these
        // lines add. It is what tells an orphan from a child whose parent is simply further down.
        Set<String> willExist = new LinkedHashSet<>(known);
        drafts.stream()
                .filter(draft -> !draft.name().isEmpty())
                .forEach(draft -> willExist.add(CsvTable.normalise(draft.name())));

        List<SimpleImportPreviewResponse.Line> lines = new ArrayList<>();
        int created = 0;
        int merged = 0;
        int skipped = 0;

        for (Draft draft : drafts) {
            List<String> notes = new ArrayList<>(draft.notes());

            if (draft.name().isEmpty()) {
                skipped++;
                lines.add(line(draft.row(), "", draft.parent(), ImportOutcome.SKIPPED,
                        ImportAction.CREATE, null, null, false,
                        List.of("Ligne sans nom de categorie : rien a creer.")));
                continue;
            }

            String folded = CsvTable.normalise(draft.name());
            if (known.contains(folded)) {
                merged++;
                notes.add("Cette categorie existe deja. L'importer ne changerait rien ; "
                        + "decochez-la, ou utilisez l'ecran Categories pour la deplacer.");
                lines.add(line(draft.row(), draft.name(), draft.parent(), ImportOutcome.MERGED,
                        ImportAction.MERGE, idOf.get(folded), draft.name(), false, notes));
                continue;
            }

            // Nothing is invented: a parent nobody defines would otherwise make this rayon a
            // root, which is the failure that looks like success.
            String parentLeaf = lastSegmentOf(draft.parent());
            if (!parentLeaf.isEmpty() && !willExist.contains(CsvTable.normalise(parentLeaf))) {
                skipped++;
                notes.add("La categorie parente \"" + draft.parent() + "\" n'existe pas et n'est "
                        + "creee par aucune ligne de ce fichier. Creez-la d'abord, ou corrigez "
                        + "l'orthographe.");
                lines.add(line(draft.row(), draft.name(), draft.parent(), ImportOutcome.SKIPPED,
                        ImportAction.CREATE, null, null, false, notes));
                continue;
            }

            created++;
            lines.add(line(draft.row(), draft.name(), draft.parent(), ImportOutcome.CREATED,
                    ImportAction.CREATE, null, null, true, notes));
        }

        List<String> recognised = new ArrayList<>();
        recognised.add("categorie : " + table.headers().get(nameAt).trim());
        if (parentAt >= 0) {
            recognised.add("parent : " + table.headers().get(parentAt).trim());
        }
        List<String> missing = parentAt < 0 ? List.of("parent") : List.of();
        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < table.headers().size(); i++) {
            String header = table.headers().get(i).trim();
            if (!header.isEmpty() && i != nameAt && i != parentAt) {
                ignored.add(header);
            }
        }

        return new SimpleImportPreviewResponse("categories", String.valueOf(table.separator()),
                lines.size(), created, merged, skipped, fields(), recognised, ignored, missing,
                List.copyOf(lines));
    }

    private static SimpleImportPreviewResponse.Line line(CsvTable.Row row, String name,
                                                         String parent, ImportOutcome outcome,
                                                         ImportAction action, Long existingId,
                                                         String existingLabel, boolean selected,
                                                         List<String> notes) {
        return new SimpleImportPreviewResponse.Line(row.line(),
                Map.of(FIELD_NAME, name, FIELD_PARENT, parent),
                outcome, action, existingId, existingLabel, selected, List.copyOf(notes));
    }

    // ----------------------------------------------------------------------- apply

    /**
     * Creates the ticked rayons, parents before children whatever the file's order.
     *
     * <p>The ordering is not cosmetic. A file listing "Eau" under "Boissons" before "Boissons"
     * itself is perfectly ordinary -- a spreadsheet sorted alphabetically produces it -- and
     * creating them in file order would make "Eau" a root and leave the tree wrong with nothing
     * to show for it. So a line whose parent has not been created yet is deferred, and the loop
     * runs again until a pass creates nothing.
     */
    @RequiresActiveLicense
    @Transactional
    public SimpleImportResultResponse apply(SimpleImportRequest request) {
        List<SimpleImportRequest.Line> waiting = request.lines().stream()
                .filter(line -> line.action() == ImportAction.CREATE)
                .toList();
        List<SimpleImportResultResponse.Line> done = new ArrayList<>();
        int created = 0;
        int merged = 0;
        int skipped = 0;

        boolean progressed = true;
        while (progressed && !waiting.isEmpty()) {
            progressed = false;
            // Rebuilt each pass rather than removed from in place: two identical lines in one
            // file are equal records, and removing by value would drop the wrong one.
            List<SimpleImportRequest.Line> deferred = new ArrayList<>();

            for (SimpleImportRequest.Line line : waiting) {
                String name = value(line, FIELD_NAME);
                String parent = value(line, FIELD_PARENT);

                if (name.isBlank()) {
                    skipped++;
                    done.add(refusal(line, name, "Ligne sans nom de categorie."));
                    progressed = true;
                    continue;
                }
                if (!parent.isBlank() && categoryService.findByPath(parent).isEmpty()) {
                    // Its parent may still be created by a later line of this same file.
                    deferred.add(line);
                    continue;
                }

                progressed = true;
                Optional<Category> already = categoryService.findByPath(name);
                if (already.isPresent()) {
                    merged++;
                    done.add(new SimpleImportResultResponse.Line(line.line(),
                            CategoryService.pathOf(already.get()), ImportOutcome.MERGED,
                            already.get().getId(),
                            "Cette categorie existait deja : rien n'a ete change."));
                    continue;
                }
                try {
                    // Through create(), not ensurePath(): a rayon asked for by name is created
                    // under exactly the rules the Categories screen applies -- depth cap, unique
                    // name, no cycle. ensurePath() is for the product import, where a too-deep
                    // path is truncated so that one awkward cell cannot cost a product.
                    Long parentId = parent.isBlank() ? null
                            : categoryService.findByPath(parent).map(Category::getId).orElse(null);
                    CategoryNodeResponse rayon =
                            categoryService.create(new CreateCategoryRequest(name, parentId));
                    created++;
                    done.add(new SimpleImportResultResponse.Line(line.line(), rayon.path(),
                            ImportOutcome.CREATED, rayon.id(), ""));
                } catch (BusinessRuleException rejection) {
                    // Too deep, or a name the shop uses elsewhere. One line's problem, not the
                    // file's: the message is the service's own and is already operator-facing.
                    skipped++;
                    done.add(refusal(line, name, rejection.getMessage()));
                }
            }
            waiting = deferred;
        }

        // Whatever is still waiting names a parent no line of this file creates either.
        for (SimpleImportRequest.Line orphan : waiting) {
            skipped++;
            done.add(refusal(orphan, value(orphan, FIELD_NAME),
                    "La categorie parente \"" + value(orphan, FIELD_PARENT)
                            + "\" n'existe pas et n'est creee par aucune ligne de ce fichier."));
        }

        done.sort(Comparator.comparingInt(SimpleImportResultResponse.Line::line));
        return new SimpleImportResultResponse(created, merged, skipped, List.copyOf(done));
    }

    private static SimpleImportResultResponse.Line refusal(SimpleImportRequest.Line line,
                                                          String label, String why) {
        return new SimpleImportResultResponse.Line(line.line(), label, ImportOutcome.SKIPPED,
                null, why);
    }

    private static String value(SimpleImportRequest.Line line, String key) {
        return Optional.ofNullable(line.values().get(key)).orElse("").trim();
    }

    /**
     * The rayon a parent cell names. Written as a path or as a bare name, it is always the last
     * segment that identifies it -- names are unique across the tree, so the ancestors in front
     * of it are context rather than part of the answer.
     */
    private static String lastSegmentOf(String path) {
        List<String> segments = CategoryService.segmentsOf(path);
        return segments.isEmpty() ? "" : segments.getLast();
    }
}
