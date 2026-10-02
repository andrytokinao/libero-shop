package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CategoryNodeResponse;
import com.houssen.liberoshop.web.dto.CreateCategoryRequest;
import com.houssen.liberoshop.web.dto.UpdateCategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that keep the rayon tree a tree.
 *
 * <p>Three of the assertions below are about damage that is invisible when it happens. A cycle
 * detaches a whole branch from every screen while its products keep pointing at it; deleting a
 * rayon that still holds goods makes those goods vanish from every filtered list; and a filter on
 * a parent that ignores its children quietly under-reports the stock. None of the three throws
 * anything on its own.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categories;
    @Mock
    private ProductRepository products;

    private CategoryService service;

    /** Stands in for the table: the mocks read and write this list. */
    private final List<Category> table = new ArrayList<>();
    private final List<Product> shelf = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        service = new CategoryService(categories, products);

        org.mockito.Mockito.when(categories.findAllWithParent()).thenAnswer(call -> List.copyOf(table));
        org.mockito.Mockito.when(categories.findAll()).thenAnswer(call -> List.copyOf(table));
        org.mockito.Mockito.when(categories.findById(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> table.stream()
                        .filter(node -> node.getId().equals(call.getArgument(0)))
                        .findFirst());
        org.mockito.Mockito.when(categories.countByParentId(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> table.stream()
                        .filter(node -> node.getParent() != null
                                && node.getParent().getId().equals(call.getArgument(0)))
                        .count());
        org.mockito.Mockito.when(categories.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenAnswer(call -> {
                    Category saved = call.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(nextId.getAndIncrement());
                    }
                    table.removeIf(node -> node.getId().equals(saved.getId()));
                    table.add(saved);
                    return saved;
                });
        org.mockito.Mockito.doAnswer(call -> {
            Category removed = call.getArgument(0);
            table.removeIf(node -> node.getId().equals(removed.getId()));
            return null;
        }).when(categories).delete(org.mockito.ArgumentMatchers.any(Category.class));
        org.mockito.Mockito.when(products.findAllWithCategory()).thenAnswer(call -> List.copyOf(shelf));
    }

    private Category rayon(String name, Category parent) {
        return categories.save(Category.builder().name(name).parent(parent).build());
    }

    private Product product(String name, Category category) {
        Product product = Product.builder()
                .name(name)
                .price(BigDecimal.valueOf(1000))
                .stockQuantity(qty(1))
                .category(category)
                .build();
        product.setId(nextId.getAndIncrement());
        shelf.add(product);
        return product;
    }

    // -------------------------------------------------------------------- the tree

    @Test
    @DisplayName("lists the tree depth-first with siblings in order, so an indent renders it")
    void treeIsDepthFirst() {
        Category boissons = rayon("Boissons", null);
        rayon("Jus", boissons);
        Category eau = rayon("Eau", boissons);
        rayon("Eau gazeuse", eau);
        rayon("Alimentaire", null);

        List<CategoryNodeResponse> tree = service.tree();

        assertEquals(List.of("Alimentaire", "Boissons", "Eau", "Eau gazeuse", "Jus"),
                tree.stream().map(CategoryNodeResponse::name).toList());
        assertEquals(List.of(0, 0, 1, 2, 1),
                tree.stream().map(CategoryNodeResponse::depth).toList());
    }

    @Test
    @DisplayName("gives each row its full path, since \"Eau\" alone does not say which branch")
    void treeCarriesThePath() {
        Category boissons = rayon("Boissons", null);
        Category eau = rayon("Eau", boissons);
        rayon("Eau gazeuse", eau);

        CategoryNodeResponse deepest = service.tree().stream()
                .filter(node -> node.name().equals("Eau gazeuse"))
                .findFirst()
                .orElseThrow();

        assertEquals("Boissons > Eau > Eau gazeuse", deepest.path());
        assertEquals(eau.getId(), deepest.parentId());
        assertEquals(0, deepest.children());
    }

    @Test
    @DisplayName("says how many rayons sit inside each one, so a leaf reads as a leaf")
    void treeCountsChildren() {
        Category boissons = rayon("Boissons", null);
        rayon("Eau", boissons);
        rayon("Jus", boissons);

        CategoryNodeResponse root = service.tree().getFirst();

        assertEquals("Boissons", root.name());
        assertEquals(2, root.children());
    }

    // ------------------------------------------------------------------- the branch

    @Test
    @DisplayName("filtering on a parent has to reach its children, or the stock under-reports")
    void branchIncludesEveryDescendant() {
        Category boissons = rayon("Boissons", null);
        Category eau = rayon("Eau", boissons);
        Category gazeuse = rayon("Eau gazeuse", eau);
        Category autre = rayon("Alimentaire", null);

        Set<Long> branch = service.branchOf(boissons.getId());

        assertEquals(Set.of(boissons.getId(), eau.getId(), gazeuse.getId()), branch);
        assertFalse(branch.contains(autre.getId()));
    }

    @Test
    @DisplayName("an unknown rayon matches nothing rather than everything")
    void branchOfUnknownIsJustItself() {
        rayon("Boissons", null);

        assertEquals(Set.of(404L), service.branchOf(404L));
        assertEquals(Set.of(), service.branchOf(null));
    }

    // ----------------------------------------------------------------------- writes

    @Test
    @DisplayName("refuses a second rayon of the same name, whatever its accents and case")
    void refusesDuplicateNames() {
        rayon("Épicerie", null);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.create(new CreateCategoryRequest("epicerie", null)));

        assertEquals("CATEGORY_NAME_TAKEN", refused.code());
    }

    @Test
    @DisplayName("keeps the operator's capitals but squeezes the spacing")
    void normalisesTheName() {
        CategoryNodeResponse created =
                service.create(new CreateCategoryRequest("  Produits   Laitiers ", null));

        assertEquals("Produits Laitiers", created.name());
        assertEquals(0, created.depth());
        assertNull(created.parentId());
    }

    @Test
    @DisplayName("a new rayon sits one level under its parent")
    void createPlacesUnderParent() {
        Category boissons = rayon("Boissons", null);

        CategoryNodeResponse created =
                service.create(new CreateCategoryRequest("Eau", boissons.getId()));

        assertEquals(1, created.depth());
        assertEquals(boissons.getId(), created.parentId());
        assertEquals("Boissons > Eau", created.path());
    }

    @Test
    @DisplayName("refuses a rayon deeper than the cap, however it is reached")
    void refusesTooDeep() {
        Category level = null;
        for (int i = 0; i < CategoryService.MAX_DEPTH; i++) {
            level = rayon("Niveau " + i, level);
        }
        Long deepest = level.getId();

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.create(new CreateCategoryRequest("Trop profond", deepest)));

        assertEquals("CATEGORY_TOO_DEEP", refused.code());
    }

    @Test
    @DisplayName("moving a branch counts the levels it carries, not just its own")
    void refusesAMoveThatWouldPushLeavesTooDeep() {
        // A two-level branch cannot go under a rayon already at the second-to-last level.
        Category a = rayon("A", null);
        Category b = rayon("B", a);
        Category branchTop = rayon("Haut", null);
        rayon("Bas", branchTop);

        Long target = b.getId();
        Long moved = branchTop.getId();
        // Haut + Bas is two levels; under B (depth 1) the leaf would land at depth 3, which
        // fits a cap of 4 levels -- so this one is allowed, and the deeper one is not.
        service.update(moved, new UpdateCategoryRequest("Haut", target));

        Category c = rayon("C", b);
        Long tooDeep = c.getId();
        Category second = rayon("Second", null);
        rayon("Second bas", second);
        Long secondMoved = second.getId();

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.update(secondMoved, new UpdateCategoryRequest("Second", tooDeep)));

        assertEquals("CATEGORY_TOO_DEEP", refused.code());
    }

    @Test
    @DisplayName("refuses making a rayon its own descendant, which would detach the branch")
    void refusesACycle() {
        Category boissons = rayon("Boissons", null);
        Category eau = rayon("Eau", boissons);

        Long parent = boissons.getId();
        Long child = eau.getId();
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.update(parent, new UpdateCategoryRequest("Boissons", child)));

        assertEquals("CATEGORY_CYCLE", refused.code());
    }

    @Test
    @DisplayName("a null parent on update promotes the rayon to the top level")
    void updateToNullParentMakesARoot() {
        Category boissons = rayon("Boissons", null);
        Category eau = rayon("Eau", boissons);

        CategoryNodeResponse promoted =
                service.update(eau.getId(), new UpdateCategoryRequest("Eau", null));

        assertEquals(0, promoted.depth());
        assertNull(promoted.parentId());
        assertEquals("Eau", promoted.path());
    }

    // ----------------------------------------------------------------------- delete

    @Test
    @DisplayName("refuses to delete a branch, which would take its children with it")
    void refusesDeletingABranch() {
        Category boissons = rayon("Boissons", null);
        rayon("Eau", boissons);

        Long id = boissons.getId();
        BusinessRuleException refused =
                assertThrows(BusinessRuleException.class, () -> service.delete(id, null));

        assertEquals("CATEGORY_HAS_CHILDREN", refused.code());
        assertTrue(refused.getMessage().contains("1"), "the count is what tells them what to do");
    }

    @Test
    @DisplayName("refuses to delete a rayon holding goods without being told where they go")
    void refusesDeletingAFullRayonBlindly() {
        Category boissons = rayon("Boissons", null);
        product("Eau 1.5L", boissons);

        Long id = boissons.getId();
        BusinessRuleException refused =
                assertThrows(BusinessRuleException.class, () -> service.delete(id, null));

        assertEquals("CATEGORY_HAS_PRODUCTS", refused.code());
        assertEquals(1, table.size(), "nothing may have been deleted");
    }

    @Test
    @DisplayName("moves the goods when told where, then deletes")
    void deletesAfterMovingTheGoods() {
        Category boissons = rayon("Boissons", null);
        Category alimentaire = rayon("Alimentaire", null);
        Product eau = product("Eau 1.5L", boissons);

        int moved = service.delete(boissons.getId(), alimentaire.getId());

        assertEquals(1, moved);
        assertSame(alimentaire, eau.getCategory());
        assertEquals(List.of("Alimentaire"), table.stream().map(Category::getName).toList());
    }

    @Test
    @DisplayName("refuses moving the goods into the rayon being deleted")
    void refusesMovingIntoItself() {
        Category boissons = rayon("Boissons", null);
        product("Eau 1.5L", boissons);

        Long id = boissons.getId();
        BusinessRuleException refused =
                assertThrows(BusinessRuleException.class, () -> service.delete(id, id));

        assertEquals("CATEGORY_SAME_TARGET", refused.code());
    }

    @Test
    @DisplayName("deletes an empty leaf without ceremony")
    void deletesAnEmptyLeaf() {
        Category boissons = rayon("Boissons", null);

        assertEquals(0, service.delete(boissons.getId(), null));
        assertTrue(table.isEmpty());
    }

    // ------------------------------------------------------------------ paths

    @Test
    @DisplayName("reads a written path however the operator separated it")
    void segmentsAcceptEverySeparator() {
        assertEquals(List.of("Boissons", "Eau"), CategoryService.segmentsOf("Boissons > Eau"));
        assertEquals(List.of("Boissons", "Eau"), CategoryService.segmentsOf("Boissons/Eau"));
        assertEquals(List.of("Boissons", "Eau"), CategoryService.segmentsOf("Boissons › Eau"));
        assertEquals(List.of("Boissons", "Eau"), CategoryService.segmentsOf("Boissons / / Eau"));
        assertEquals(List.of(), CategoryService.segmentsOf("   "));
        assertEquals(List.of(), CategoryService.segmentsOf(null));
    }

    @Test
    @DisplayName("matches a path on its last segment, so a wrong parent still finds the rayon")
    void findByPathMatchesTheLeaf() {
        Category boissons = rayon("Boissons", null);
        Category eau = rayon("Eau", boissons);

        assertEquals(Optional.of(eau), service.findByPath("Boissons > Eau"));
        assertEquals(Optional.of(eau), service.findByPath("eau"));
        assertEquals(Optional.of(eau), service.findByPath("Alimentaire > Eau"));
        assertEquals(Optional.empty(), service.findByPath("Surgeles"));
    }

    @Test
    @DisplayName("creates only the levels of a path that are missing")
    void ensurePathFillsTheGaps() {
        Category boissons = rayon("Boissons", null);

        Category leaf = service.ensurePath("Boissons > Eau > Eau gazeuse").orElseThrow();

        assertEquals("Eau gazeuse", leaf.getName());
        assertEquals("Boissons > Eau > Eau gazeuse", CategoryService.pathOf(leaf));
        assertEquals(3, table.size(), "Boissons existed, only two rayons were created");
        assertSame(boissons, leaf.getParent().getParent());
    }

    @Test
    @DisplayName("drops the segments past the cap rather than failing the whole import")
    void ensurePathTruncatesRatherThanRefusing() {
        StringBuilder path = new StringBuilder("N0");
        for (int i = 1; i <= CategoryService.MAX_DEPTH + 2; i++) {
            path.append(" > N").append(i);
        }

        Category leaf = service.ensurePath(path.toString()).orElseThrow();

        assertEquals("N" + (CategoryService.MAX_DEPTH - 1), leaf.getName());
        assertEquals(CategoryService.MAX_DEPTH, table.size());
    }

    @Test
    @DisplayName("a blank path creates nothing at all")
    void ensurePathOfNothing() {
        assertEquals(Optional.empty(), service.ensurePath("  "));
        assertTrue(table.isEmpty());
    }
}
