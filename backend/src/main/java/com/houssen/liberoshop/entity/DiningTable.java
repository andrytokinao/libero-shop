package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A table customers order from by scanning its QR code.
 *
 * <p>The code carries {@code token}, never the name or the id: a guessable link would let
 * anyone order for "Table 99" from home. Regenerating the token is how a code that went around
 * is withdrawn -- the printed QR stops working, the table keeps its name.
 */
@Entity
@Table(name = "dining_table")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "Table 4", "Terrasse 2", "Bar" -- what the waiter reads on the order. */
    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String token;
}
