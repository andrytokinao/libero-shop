package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An account holds one or more roles, and both the label and the landing page have to hold
 * for either case -- the depot that splits the duties and the grocery where one person does
 * everything.
 */
class RolePolicyTest {

    @Test
    @DisplayName("names a single-role account by its job title")
    void namesOneJobInFull() {
        assertEquals("Responsable de caisse", RolePolicy.labelOf(EnumSet.of(RoleApp.CASHIER)));
    }

    @Test
    @DisplayName("names an account that holds several roles by its short labels")
    void namesSeveralJobsShort() {
        // Four full titles would wrap over three lines in the sidebar and say less.
        assertEquals("Caisse · Depot",
                RolePolicy.labelOf(EnumSet.of(RoleApp.DEPOT_AGENT, RoleApp.CASHIER)));
    }

    @Test
    @DisplayName("opens on the counter when the account also sells")
    void landsOnTheMostOperationalRole() {
        // The owner of a one-person grocery logs in to sell, not to read a report.
        assertEquals("/caisse/tableau-de-bord", RolePolicy.homePathOf(EnumSet.allOf(RoleApp.class)));
        assertEquals("/admin/vue-ensemble", RolePolicy.homePathOf(EnumSet.of(RoleApp.SUPER_ADMIN)));
    }

    @Test
    @DisplayName("orders roles the same way wherever they are listed")
    void ordersRolesByPrecedence() {
        assertEquals(List.of(RoleApp.CASHIER, RoleApp.DEPOT_MANAGER, RoleApp.SUPER_ADMIN),
                RolePolicy.ordered(EnumSet.of(RoleApp.SUPER_ADMIN, RoleApp.CASHIER, RoleApp.DEPOT_MANAGER)));
    }

    @Test
    @DisplayName("refuses to decide anything for an account with no role")
    void refusesARolelessAccount() {
        Set<RoleApp> none = EnumSet.noneOf(RoleApp.class);
        assertThrows(IllegalStateException.class, () -> RolePolicy.primaryOf(none));
    }
}
