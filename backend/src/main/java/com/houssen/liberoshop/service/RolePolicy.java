package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The human label and the landing route of a set of roles.
 *
 * <p>Served to the client rather than duplicated there: adding a role means editing this
 * switch and the router, not hunting for a second mapping table in the front end. The
 * compiler flags the missing branch because the switches are exhaustive over the enum.
 *
 * <p>An account holds one or more roles, so both answers are decided over the whole set.
 * The landing page belongs to the role of highest {@link #PRECEDENCE}, which puts the
 * counter first: whoever cumulates the jobs of a small grocery opens the application in the
 * morning to sell, not to read reports.
 */
public final class RolePolicy {

    /**
     * Which role speaks for an account that holds several, most operational first.
     *
     * <p>Order of business, not of authority: the owner of a one-person grocery holds every
     * role, and sending them to the administration overview every morning would put a report
     * between them and their first customer.
     */
    private static final List<RoleApp> PRECEDENCE =
            List.of(RoleApp.CASHIER, RoleApp.DEPOT_AGENT, RoleApp.DEPOT_MANAGER, RoleApp.SUPER_ADMIN);

    private RolePolicy() {
    }

    /** Full title of a single role, as an employment contract would name it. */
    public static String labelOf(RoleApp role) {
        return switch (role) {
            case CASHIER -> "Responsable de caisse";
            case DEPOT_AGENT -> "Agent de depot";
            case DEPOT_MANAGER -> "Responsable entree-sortie depot";
            case SUPER_ADMIN -> "Super admin";
        };
    }

    /**
     * One word for a role, for the accounts that hold several.
     *
     * <p>Four full titles in the sidebar would wrap over three lines and say less than
     * "Caisse · Depot · Stock · Admin" does.
     */
    public static String shortLabelOf(RoleApp role) {
        return switch (role) {
            case CASHIER -> "Caisse";
            case DEPOT_AGENT -> "Depot";
            case DEPOT_MANAGER -> "Stock";
            case SUPER_ADMIN -> "Admin";
        };
    }

    /** Full title when the account has one job, the list of short ones when it has several. */
    public static String labelOf(Collection<RoleApp> roles) {
        List<RoleApp> ordered = ordered(roles);
        if (ordered.size() == 1) {
            return labelOf(ordered.getFirst());
        }
        return ordered.stream().map(RolePolicy::shortLabelOf).collect(Collectors.joining(" · "));
    }

    public static String homePathOf(RoleApp role) {
        return switch (role) {
            case CASHIER -> "/caisse/tableau-de-bord";
            case DEPOT_AGENT -> "/depot/tableau-de-bord";
            case DEPOT_MANAGER -> "/gestion-depot/tableau-de-bord";
            case SUPER_ADMIN -> "/admin/vue-ensemble";
        };
    }

    public static String homePathOf(Collection<RoleApp> roles) {
        return homePathOf(primaryOf(roles));
    }

    /**
     * The role an account is named after, and whose landing page it opens on.
     *
     * @throws IllegalStateException if the account holds no role at all -- it could not be
     *                               sent anywhere, and the entity refuses to be saved that
     *                               way, so this can only come from a hand-edited database
     */
    public static RoleApp primaryOf(Collection<RoleApp> roles) {
        return PRECEDENCE.stream()
                .filter(roles::contains)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Compte sans aucun role : impossible de decider de sa page d'accueil."));
    }

    /** The given roles in order of precedence, so every list in the UI reads the same way. */
    public static List<RoleApp> ordered(Collection<RoleApp> roles) {
        return PRECEDENCE.stream().filter(roles::contains).toList();
    }
}
