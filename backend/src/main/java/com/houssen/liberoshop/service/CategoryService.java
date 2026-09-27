package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import com.houssen.liberoshop.web.dto.CreateCategoryRequest;
import com.houssen.liberoshop.web.dto.UpdateCategoryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The rayons of the shop, as a tree.
 *
 * <p>Every read here loads the whole table once and works on it in memory. That reads as
 * wasteful until you count: a grocery has a few dozen rayons, the tree is needed whole
 * anyway -- to order it, to indent it, to print a path, to refuse a cycle -- and the
 * alternative is a recursive query per row. The one place this would have to change is a
 * chain of shops sharing a catalogue of thousands of rayons, which this product is not.
 *
 * <p>Writes are licensed, reads are not: an expired license makes the application read-only,
 * and a cashier still has to be able to see which rayon a product belongs to.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    /**
     * How deep a rayon may sit. Four levels is more than a grocery uses -- "Boissons &gt;
     * Sans alcool &gt; Eau &gt; Eau gazeuse" already feels laboured -- and the cap is what
     * stops an import file from growing a path forever by accident.
     */
    public static final int MAX_DEPTH = 4;

    /** How a path is written back to the operator. */
    public static final String PATH_SEPARATOR = " > ";

    /**
     * What a path may be written with in an import file. Excel users reach for whichever of
     * these is on their keyboard, and a shopkeeper typing "Boissons/Eau" means the same thing
     * as one typing "Boissons > Eau".
     */
    private static final String PATH_SPLIT_PATTERN = "\\s*[>/›»|]\\s*";

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    // ----------------------------------------------------------------------- reads

    /**
     * The whole tree, depth-first, siblings in alphabetical order -- so a list rendered in
     * this order with {@code depth} as an indent already looks like the tree.
     */
    public List<CategoryNodeResponse> tree() {
        return nodesOf(categories.findAllWithParent());
    }

    /**
     * A rayon and everything below it.
     *
     * <p>What "filter by Boissons" has to mean once Boissons has children: a shopkeeper asking
     * for the drinks expects the waters and the juices, not the handful of bottles that happened
     * to be filed on the parent itself. An unknown id answers with just that id, so a filter on
     * a rayon deleted in another tab returns nothing rather than everything.
     */
    public Set<Long> branchOf(Long rootId) {
        if (rootId == null) {
            return Set.of();
        }
        List<Category> all = categories.findAllWithParent();
        Set<Long> branch = new LinkedHashSet<>();
        branch.add(rootId);
        // One pass per level, not recursion: MAX_DEPTH bounds it, and a cycle in a
        // hand-edited table would otherwise never come back.
        for (int level = 0; level < MAX_DEPTH; level++) {
            List<Long> next = all.stream()
                    .filter(node -> node.getParent() != null
                            && branch.contains(node.getParent().getId())
                            && !branch.contains(node.getId()))
                    .map(Category::getId)
                    .toList();
            if (next.isEmpty()) {
                break;
            }
            branch.addAll(next);
        }
        return Set.copyOf(branch);
    }

    // ---------------------------------------------------------------------- writes

    @RequiresActiveLicense
    @Transactional
    public CategoryNodeResponse create(CreateCategoryRequest request) {
        String name = normaliseName(request.name());
        refuseDuplicate(name, null);

        Category parent = request.parentId() == null ? null : require(request.parentId());
        refuseTooDeep(parent);

        Category saved = categories.save(Category.builder().name(name).parent(parent).build());
        return nodeOf(saved, depthOf(parent) + 1, 0);
    }

    /**
     * Renames a rayon, and moves it if asked.
     *
     * <p>Moving carries the branch below it, which is why the depth check is on the deepest
     * descendant rather than on this row: dropping a two-level branch under a level-three
     * rayon would otherwise push its leaves past {@link #MAX_DEPTH}.
     */
    @RequiresActiveLicense
    @Transactional
    public CategoryNodeResponse update(Long id, UpdateCategoryRequest request) {
        Category category = require(id);
        String name = normaliseName(request.name());
        refuseDuplicate(name, id);

        List<Category> all = categories.findAllWithParent();
        Category parent = request.parentId() == null ? null : require(request.parentId());
        if (parent != null) {
            refuseCycle(category, parent, all);
            refuseTooDeep(parent, subtreeHeight(category, all));
        }

        category.setName(name);
        category.setParent(parent);
        return nodeOf(category, depthOf(parent) + 1, (int) categories.countByParentId(id));
    }

    /**
     * Removes a rayon, moving its goods somewhere first if it still holds any.
     *
     * <p>A sub-branch is always refused: deleting "Boissons" cannot be allowed to take "Eau"
     * and "Jus" with it, because one click would then destroy a part of the catalogue's
     * organisation that took an afternoon to build. Emptying the branch is the operator's
     * decision, one rayon at a time.
     *
     * <p>Goods are different -- they must land somewhere, and leaving them in no rayon at all
     * would make them vanish from every filtered list without anything visible having
     * happened to them. So the caller either names where they go, or is told how many are in
     * the way. Both paths go through here rather than through a separate "move" endpoint: the
     * move only ever exists in order to delete, and splitting it would let a screen move the
     * goods and then fail to delete, leaving a rayon that is empty for no reason.
     *
     * @param moveProductsTo where the goods go, or null to refuse rather than guess
     * @return how many products were moved
     */
    @RequiresActiveLicense
    @Transactional
    public int delete(Long id, Long moveProductsTo) {
        Category category = require(id);
        long children = categories.countByParentId(id);
        if (children > 0) {
            throw new BusinessRuleException("CATEGORY_HAS_CHILDREN",
                    "\"" + category.getName() + "\" contient " + children
                            + " sous-categorie(s). Supprimez-les ou deplacez-les d'abord.");
        }

        List<Product> held = products.findAllWithCategory().stream()
                .filter(product -> product.getCategory() != null
                        && product.getCategory().getId().equals(id))
                .toList();
        if (!held.isEmpty() && moveProductsTo == null) {
            throw new BusinessRuleException("CATEGORY_HAS_PRODUCTS",
                    "\"" + category.getName() + "\" contient encore " + held.size()
                            + " produit(s). Choisissez la categorie qui doit les recevoir.");
        }
        if (!held.isEmpty()) {
            if (id.equals(moveProductsTo)) {
                throw new BusinessRuleException("CATEGORY_SAME_TARGET",
                        "Les produits ne peuvent pas etre deplaces dans la categorie supprimee.");
            }
            Category target = require(moveProductsTo);
            held.forEach(product -> product.setCategory(target));
        }

        categories.delete(category);
        return held.size();
    }

    // ------------------------------------------------------------ paths, for imports

    /**
     * Finds the rayon a written path names, without creating anything.
     *
     * <p>Only the last segment is matched, because names are unique across the tree: a file
     * that says {@code Boissons > Eau} and one that says just {@code Eau} point at the same
     * row, and a file whose parent segment is wrong still finds the rayon the operator meant.
     *
     * @param path as written in the file; blank gives an empty result
     */
    public Optional<Category> findByPath(String path) {
        return findByPath(path, categories.findAllWithParent());
    }

    /** As {@link #findByPath(String)}, against a tree already in hand. */
    public Optional<Category> findByPath(String path, List<Category> all) {
        List<String> segments = segmentsOf(path);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        String leaf = CsvTable.normalise(segments.getLast());
        return all.stream().filter(c -> CsvTable.normalise(c.getName()).equals(leaf)).findFirst();
    }

    /**
     * The rayon a written path names, creating the levels that do not exist yet.
     *
     * <p>Used by the product import: a file that carries its own rayons should not have to be
     * preceded by an afternoon of typing them in. {@code Boissons > Eau} against a shop that
     * only has "Boissons" creates "Eau" inside it; against an empty catalogue it creates
     * both.
     *
     * <p>Segments that would go past {@link #MAX_DEPTH} are dropped rather than refused -- the
     * deepest rayon that fits is used. A refusal here would fail a whole import over one
     * over-precise cell, and the operator can still move the product afterwards.
     *
     * <p>The cap is measured on the resulting depth, not on how many segments the path has.
     * Those are different numbers whenever the path starts from a rayon that already sits deep:
     * {@code "Niveau 3 > Trop profond"} is two segments and would still land at the fifth level.
     * Counting segments alone is how a tree quietly grows past its own limit.
     */
    @Transactional
    public Optional<Category> ensurePath(String path) {
        List<String> segments = segmentsOf(path);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        List<Category> all = new ArrayList<>(categories.findAllWithParent());
        Category parent = null;
        Category current = null;
        for (String segment : segments) {
            String name = normaliseName(segment);
            String folded = CsvTable.normalise(name);
            Optional<Category> existing = all.stream()
                    .filter(c -> CsvTable.normalise(c.getName()).equals(folded))
                    .findFirst();
            if (existing.isPresent()) {
                current = existing.get();
            } else {
                if (depthOf(parent) + 1 > MAX_DEPTH - 1) {
                    // No room below. The deepest rayon reached so far is the answer, and the
                    // product lands there rather than nowhere.
                    break;
                }
                current = categories.save(Category.builder().name(name).parent(parent).build());
                all.add(current);
            }
            parent = current;
        }
        return Optional.ofNullable(current);
    }

    /** The path of a rayon as it is shown, e.g. {@code "Boissons > Eau"}. */
    public static String pathOf(Category category) {
        List<String> names = new ArrayList<>();
        for (Category node = category; node != null; node = node.getParent()) {
            names.add(node.getName());
            if (names.size() > MAX_DEPTH + 1) {
                // Only reachable if a cycle got into the table behind our back; better a
                // truncated label than a screen that never finishes rendering.
                break;
            }
        }
        return String.join(PATH_SEPARATOR, names.reversed());
    }

    /** The segments of a written path, blanks dropped: {@code "Boissons / / Eau"} is two. */
    public static List<String> segmentsOf(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(PATH_SPLIT_PATTERN))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    // ------------------------------------------------------------------- internals

    /**
     * Depth-first over the loaded rows: children grouped by parent, each group alphabetical.
     *
     * <p>Rows whose parent is missing from the list cannot happen through this service, but a
     * hand-edited database could produce one; they are emitted as roots rather than dropped,
     * so a rayon still holding goods never disappears from the screen that could fix it.
     */
    private static List<CategoryNodeResponse> nodesOf(List<Category> all) {
        Map<Long, List<Category>> childrenOf = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        Map<Long, Category> byId = new LinkedHashMap<>();
        all.forEach(category -> byId.put(category.getId(), category));

        for (Category category : all) {
            Category parent = category.getParent();
            if (parent == null || !byId.containsKey(parent.getId())) {
                roots.add(category);
            } else {
                childrenOf.computeIfAbsent(parent.getId(), key -> new ArrayList<>()).add(category);
            }
        }

        Comparator<Category> byName = Comparator.comparing(Category::getName,
                String.CASE_INSENSITIVE_ORDER);
        roots.sort(byName);
        childrenOf.values().forEach(siblings -> siblings.sort(byName));

        List<CategoryNodeResponse> flattened = new ArrayList<>(all.size());
        for (Category root : roots) {
            appendBranch(root, 0, childrenOf, flattened);
        }
        return List.copyOf(flattened);
    }

    private static void appendBranch(Category category, int depth,
                                     Map<Long, List<Category>> childrenOf,
                                     List<CategoryNodeResponse> out) {
        List<Category> children = childrenOf.getOrDefault(category.getId(), List.of());
        out.add(nodeOf(category, depth, children.size()));
        // A tree deeper than the writes allow can only come from a hand-edited database.
        // Stopping here keeps the response finite instead of trusting the data.
        if (depth + 1 >= MAX_DEPTH) {
            return;
        }
        for (Category child : children) {
            appendBranch(child, depth + 1, childrenOf, out);
        }
    }

    private static CategoryNodeResponse nodeOf(Category category, int depth, int children) {
        return new CategoryNodeResponse(
                category.getId(),
                category.getName(),
                category.getParent() == null ? null : category.getParent().getId(),
                depth,
                pathOf(category),
                children);
    }

    private Category require(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Categorie", id));
    }

    /**
     * Trimmed and squeezed, never case-folded: the operator's capitals are theirs. What is
     * folded is the comparison, so "boissons" does not become a second "Boissons".
     */
    private static String normaliseName(String name) {
        String trimmed = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("CATEGORY_NAME_REQUIRED",
                    "Le nom de la categorie est obligatoire.");
        }
        return trimmed;
    }

    private void refuseDuplicate(String name, Long selfId) {
        String folded = CsvTable.normalise(name);
        boolean taken = categories.findAll().stream()
                .filter(other -> selfId == null || !selfId.equals(other.getId()))
                .anyMatch(other -> CsvTable.normalise(other.getName()).equals(folded));
        if (taken) {
            throw new BusinessRuleException("CATEGORY_NAME_TAKEN",
                    "Une categorie \"" + name + "\" existe deja. Les noms de categories sont "
                            + "uniques dans tout l'arbre, pour qu'un fichier d'import qui "
                            + "n'ecrit qu'un nom designe toujours le meme rayon.");
        }
    }

    private void refuseTooDeep(Category parent) {
        refuseTooDeep(parent, 1);
    }

    /**
     * @param height levels in the branch being placed, itself counted -- 1 for a new rayon or
     *               a moved leaf, 2 for a moved rayon that has children of its own
     */
    private void refuseTooDeep(Category parent, int height) {
        // depthOf(parent) + 1 is where the branch's top lands; + height - 1 its deepest leaf.
        if (depthOf(parent) + height > MAX_DEPTH - 1) {
            throw new BusinessRuleException("CATEGORY_TOO_DEEP",
                    "Les categories ne peuvent pas depasser " + MAX_DEPTH + " niveaux.");
        }
    }

    /**
     * Zero-based depth, the same number {@link CategoryNodeResponse#depth()} carries: 0 for a
     * root, 1 for its children. Null answers -1, so that {@code depthOf(parent) + 1} is the
     * depth of a child of {@code parent} whether or not that parent exists.
     */
    private static int depthOf(Category category) {
        int depth = -1;
        for (Category node = category; node != null; node = node.getParent()) {
            depth++;
            if (depth > MAX_DEPTH) {
                break;
            }
        }
        return depth;
    }

    /**
     * Refuses making a rayon its own ancestor. Without this a two-click mistake in the
     * category screen -- move "Boissons" under "Eau" -- detaches the whole branch from the
     * tree: it stops appearing anywhere, while its products keep pointing at it.
     */
    private static void refuseCycle(Category category, Category newParent, List<Category> all) {
        Map<Long, Category> byId = new HashMap<>();
        all.forEach(node -> byId.put(node.getId(), node));
        for (Category node = newParent; node != null; node = parentOf(node, byId)) {
            if (node.getId().equals(category.getId())) {
                throw new BusinessRuleException("CATEGORY_CYCLE",
                        "\"" + category.getName() + "\" ne peut pas etre placee dans "
                                + "\"" + newParent.getName() + "\", qui est deja en dessous d'elle.");
            }
        }
    }

    private static Category parentOf(Category node, Map<Long, Category> byId) {
        return node.getParent() == null ? null : byId.get(node.getParent().getId());
    }

    /** Levels in the branch rooted at this rayon, itself counted: a leaf is 1. */
    private static int subtreeHeight(Category root, List<Category> all) {
        int height = 1;
        Set<Long> level = Set.of(root.getId());
        while (height <= MAX_DEPTH) {
            Set<Long> parents = level;
            Set<Long> next = all.stream()
                    .filter(node -> node.getParent() != null
                            && parents.contains(node.getParent().getId()))
                    .map(Category::getId)
                    .collect(Collectors.toUnmodifiableSet());
            if (next.isEmpty()) {
                break;
            }
            height++;
            level = next;
        }
        return height;
    }
}
