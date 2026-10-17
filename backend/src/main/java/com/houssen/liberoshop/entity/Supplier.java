package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supplier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String contact;

    // Simple text today; could become a @ManyToMany with Product later.
    private String suppliedProducts;

    /**
     * No longer offered when a receipt is booked; its past receipts and prices stay. False on every
     * supplier of an older database.
     */
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable = false)
    private boolean retired;
}
