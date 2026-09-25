package com.houssen.libertyshop.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    /** Login handle. Unique, and what Spring Security authenticates against. */
    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt hash, never the clear password. No getter is exposed over HTTP. */
    @Column(nullable = false)
    private String password;

    /** Lets an account be revoked without deleting the sales history it is linked to. */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleApp role;
}
