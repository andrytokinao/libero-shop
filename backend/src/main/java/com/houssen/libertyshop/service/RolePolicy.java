package com.houssen.libertyshop.service;

import com.houssen.libertyshop.entity.RoleApp;

/**
 * The human label and the landing route of each role.
 *
 * <p>Served to the client rather than duplicated there: adding a role means editing this
 * switch and the router, not hunting for a second mapping table in the front end. The
 * compiler flags the missing branch because the switches are exhaustive over the enum.
 */
public final class RolePolicy {

    private RolePolicy() {
    }

    public static String labelOf(RoleApp role) {
        return switch (role) {
            case CASHIER -> "Responsable de caisse";
            case DEPOT_AGENT -> "Agent de depot";
            case DEPOT_MANAGER -> "Responsable entree-sortie depot";
            case SUPER_ADMIN -> "Super admin";
        };
    }

    public static String homePathOf(RoleApp role) {
        return switch (role) {
            case CASHIER -> "/caisse/tableau-de-bord";
            case DEPOT_AGENT -> "/depot/tableau-de-bord";
            case DEPOT_MANAGER -> "/gestion-depot/tableau-de-bord";
            case SUPER_ADMIN -> "/admin/vue-ensemble";
        };
    }
}
