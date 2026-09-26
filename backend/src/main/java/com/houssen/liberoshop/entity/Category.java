package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A rayon of the shop, possibly inside a wider one: "Boissons" holds "Eau" and "Jus".
 *
 * <p>The tree is one self-reference and nothing else -- no closure table, no materialised
 * path. A grocery's rayons are a few dozen rows read in one query, and walking the parents
 * in memory costs less than keeping a second structure true.
 *
 * <p>{@code name} stays unique across the whole tree, not only among siblings, and that is
 * deliberate. An import file writes {@code Boissons > Eau} or just {@code Eau}, and the
 * second form has to mean one rayon; two "Eau" under different parents would turn every
 * such line into a question nobody can answer from the file. Depth is capped by
 * {@code CategoryService.MAX_DEPTH}.
 */
@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** The wider rayon this one sits in, or null for a root. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
}
