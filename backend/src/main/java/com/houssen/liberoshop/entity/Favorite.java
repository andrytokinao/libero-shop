package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Something a seller pinned to find it at once: a product, or a whole rayon.
 *
 * <p>Personal: each account has its own. The cashier at the bakery counter and the one at the
 * hardware counter do not reach for the same things, and a shared list would be every seller's
 * habits at once -- long, and nobody's.
 *
 * <p>Exactly one of {@code product} and {@code category} is set; {@code FavoriteService} is the
 * only writer and keeps it so. Removing the product, the rayon or the account takes the pin with
 * it, in the database itself, so nothing that deletes them has to know favourites exist.
 */
@Entity
@Table(name = "user_favorite", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "product_id"}),
        @UniqueConstraint(columnNames = {"user_id", "category_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserApp user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Category category;

    @Column(nullable = false)
    private LocalDateTime pinnedAt;
}
