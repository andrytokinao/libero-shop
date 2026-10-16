package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of units kept to be reused on product after product: "Poids" = g, kg, tonne.
 *
 * <p>A model holds no price and no stock, only how its units relate to one another, counted in
 * its own reference unit ({@link #baseLabel}): "kg = 1000 g", "tonne = 1000 kg". Applied to a
 * product, its units are <em>copied</em> onto it as ordinary packagings with the product's own
 * prices; renaming or deleting the model afterwards changes no product.
 *
 * <p>Its units are kept in the order they were added, and each may only be written in the
 * reference unit or in a unit added before it: that order is the one they can be created in.
 */
@Entity
@Table(name = "unit_model")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitModel {

    public static final int MAX_NAME_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_NAME_LENGTH)
    private String name;

    /** The unit the others are counted in, as typed: "g". */
    @Column(nullable = false, length = Product.MAX_UNIT_LENGTH)
    private String baseLabel;

    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    @Builder.Default
    private List<UnitModelUnit> units = new ArrayList<>();

}
