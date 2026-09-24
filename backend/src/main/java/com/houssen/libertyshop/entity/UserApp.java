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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleApp role;
}
