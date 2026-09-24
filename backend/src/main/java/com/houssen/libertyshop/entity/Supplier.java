package com.houssen.libertyshop.entity;

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
}
