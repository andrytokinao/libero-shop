package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * The picture a rayon shows on the sale screens' folder tiles, chosen in the category admin.
 *
 * <p>Kept apart from {@link Category} like a profile photo from its account: the tree is read
 * whole on every screen, and its rows must stay a few bytes each, not a few tens of kilobytes.
 * Removing the rayon removes its picture, in the database itself.
 */
@Entity
@Table(name = "category_image")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryImage {

    /** A shrunk square from the browser is a few tens of kilobytes; this is a generous ceiling. */
    public static final int MAX_BYTES = 512 * 1024;

    @Id
    private Long categoryId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Category category;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false, length = 32)
    private String contentType;
}
