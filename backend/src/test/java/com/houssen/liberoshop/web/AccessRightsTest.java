package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.InvoiceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.InvoiceAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a signed-in account may still do once something about it has changed, and what each role
 * may read -- through the real filter chain, with the real tokens.
 *
 * <p>Rolled back after each test: the accounts of the starter dataset are edited here, and the
 * rest of the suite signs in with them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessRightsTest {

    private static final String PASSWORD = "vaha2026";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private JsonMapper json;

    // ------------------------------------------------------------- the account, re-read

    @Test
    @DisplayName("un compte desactive est refuse a la requete suivante, lectures comprises")
    void aDisabledAccountIsCutOffAtOnce() throws Exception {
        String token = signIn("hary");
        mvc.perform(get("/api/invoices").param("mine", "true").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        account("hary").setEnabled(false);

        mvc.perform(get("/api/invoices").param("mine", "true").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/auth/session").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un role retire ne sert plus, un role donne sert tout de suite, sans se reconnecter")
    void rolesAreReadFromTheAccount() throws Exception {
        String admin = signIn("mparany");
        mvc.perform(get("/api/users").header("Authorization", bearer(admin))).andExpect(status().isOk());

        // Demoted while signed in: the token still says nothing else than who he is.
        UserApp mparany = account("mparany");
        mparany.getRoles().clear();
        mparany.getRoles().add(RoleApp.CASHIER);

        mvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/auth/session").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_CASHIER"))
                .andExpect(jsonPath("$.authorities.length()").value(1));

        // And the other way: a cashier made responsible for the stock reads it on her next call.
        String cashier = signIn("fatima");
        mvc.perform(get("/api/suppliers").header("Authorization", bearer(cashier))).andExpect(status().isForbidden());
        account("fatima").getRoles().add(RoleApp.DEPOT_MANAGER);
        mvc.perform(get("/api/suppliers").header("Authorization", bearer(cashier))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------- sessions closed

    @Test
    @DisplayName("un changement de mot de passe ferme toutes les sessions du compte")
    void aNewPasswordClosesEverySession() throws Exception {
        String onTheTill = signIn("hary");
        String onThePhone = signIn("hary");
        String admin = signIn("mparany");

        mvc.perform(put("/api/users/{id}/password", account("hary").getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"nouveau-secret"}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/session").header("Authorization", bearer(onTheTill)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session").header("Authorization", bearer(onThePhone)))
                .andExpect(status().isUnauthorized());
        // The administrator's own session is somebody else's: untouched.
        mvc.perform(get("/api/auth/session").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        // He comes back in with the new password, and only with it.
        mvc.perform(login("hary", PASSWORD)).andExpect(status().isUnauthorized());
        String again = tokenOf(mvc.perform(login("hary", "nouveau-secret")).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/auth/session").header("Authorization", bearer(again)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    @DisplayName("la deconnexion ferme la session de cet appareil, pas celle des autres")
    void signingOutClosesThatDeviceOnly() throws Exception {
        String onTheTill = signIn("fatima");
        String onThePhone = signIn("fatima");

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(onTheTill)))
                .andExpect(status().isNoContent());

        // The token left behind in the browser opens nothing any more...
        mvc.perform(get("/api/invoices").param("mine", "true").header("Authorization", bearer(onTheTill)))
                .andExpect(status().isUnauthorized());
        // ...while her phone carries on.
        mvc.perform(get("/api/invoices").param("mine", "true").header("Authorization", bearer(onThePhone)))
                .andExpect(status().isOk());

        // Signing out with nothing to sign out of is not an error.
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    // --------------------------------------------------------------- what each role reads

    @Test
    @DisplayName("fournisseurs et sorties de stock : reserves au depot et a l'administration")
    void purchasesAndStockOutputsAreForThoseWhoAnswerForThem() throws Exception {
        for (String counter : List.of("fatima", "joseph", "lova")) {
            String token = signIn(counter);
            mvc.perform(get("/api/suppliers").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/stock/outputs").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        for (String responsible : List.of("nadia", "mparany")) {
            String token = signIn(responsible);
            mvc.perform(get("/api/suppliers").header("Authorization", bearer(token))).andExpect(status().isOk());
            mvc.perform(get("/api/stock/outputs").header("Authorization", bearer(token))).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("factures : tout pour les responsables, le travail du jour pour la caisse et le depot, les siennes pour la prise de commande")
    void invoicesAreNarrowedToEachRole() throws Exception {
        Invoice old = closedInvoiceOfAnotherDay("fatima", "hary", "joseph");
        Set<Long> everything = Set.copyOf(invoices.findAll().stream().map(Invoice::getId).toList());

        // Whoever runs the shop or the stock reads the whole history.
        assertEquals(everything, idsListedFor("mparany"));
        assertEquals(everything, idsListedFor("nadia"));

        // The other cashier works on today's orders and the open ones -- not on last week's
        // closed sale of a colleague, in a list or by its number.
        Set<Long> hary = idsListedFor("hary");
        assertFalse(hary.contains(old.getId()), "a closed sale of another day, sold by a colleague");
        assertTrue(hary.size() < everything.size());
        mvc.perform(get("/api/invoices/{id}", old.getId()).header("Authorization", bearer(signIn("hary"))))
                .andExpect(status().isForbidden());
        // The storekeeper likewise.
        assertFalse(idsListedFor("joseph").contains(old.getId()));

        // The one who sold it still finds it among her invoices.
        assertTrue(idsListedFor("fatima").contains(old.getId()));
        mvc.perform(get("/api/invoices/{id}", old.getId()).header("Authorization", bearer(signIn("fatima"))))
                .andExpect(status().isOk());

        // An order taker reads the orders she took, whatever she asks for.
        Long lova = account("lova").getId();
        for (Long id : idsListedFor("lova")) {
            Invoice invoice = invoices.findDetailedById(id).orElseThrow();
            assertEquals(lova, invoice.getSale().getSeller().getId(), "order " + invoice.getInvoiceNumber());
        }
        Long someoneElses = everything.stream()
                .filter(id -> !lova.equals(invoices.findDetailedById(id).orElseThrow().getSale().getSeller().getId()))
                .findFirst().orElseThrow();
        mvc.perform(get("/api/invoices/{id}", someoneElses).header("Authorization", bearer(signIn("lova"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le role le plus large l'emporte pour un compte qui en cumule plusieurs")
    void theWidestRoleDecides() {
        assertEquals(InvoiceAccess.Scope.OWN, InvoiceAccess.scopeOf(EnumSet.of(RoleApp.ORDER_TAKER)));
        assertEquals(InvoiceAccess.Scope.CURRENT,
                InvoiceAccess.scopeOf(EnumSet.of(RoleApp.ORDER_TAKER, RoleApp.DEPOT_AGENT)));
        assertEquals(InvoiceAccess.Scope.CURRENT, InvoiceAccess.scopeOf(EnumSet.of(RoleApp.CASHIER)));
        assertEquals(InvoiceAccess.Scope.EVERYTHING,
                InvoiceAccess.scopeOf(EnumSet.of(RoleApp.CASHIER, RoleApp.DEPOT_MANAGER)));
        assertEquals(InvoiceAccess.Scope.EVERYTHING, InvoiceAccess.scopeOf(EnumSet.allOf(RoleApp.class)));
    }

    @Test
    @DisplayName("versements : la caisse les lit tous, les autres seulement les leurs")
    void remittancesAreForTheTillAndTheirSubmitter() throws Exception {
        JsonNode all = listed("/api/remittances", "fatima");
        assertTrue(all.size() > 0, "the starter dataset carries slips handed in by the depot");

        // Asking for everything, the storekeeper gets what he handed in himself.
        Long joseph = account("joseph").getId();
        for (JsonNode slip : listed("/api/remittances", "joseph")) {
            assertEquals(joseph, slip.get("submittedBy").get("id").asLong());
        }
        // The stock manager and the order taker handed nothing in: nothing to read.
        assertEquals(0, listed("/api/remittances", "nadia").size());
        assertEquals(0, listed("/api/remittances", "lova").size());
        assertEquals(all.size(), listed("/api/remittances", "mparany").size());
    }

    // -------------------------------------------------------------------------- helpers

    /**
     * A sale of the dataset moved to last week and closed -- paid, handed over -- in which none
     * of {@code strangers} had a hand: they neither served it nor touched its money.
     */
    private Invoice closedInvoiceOfAnotherDay(String sellerUsername, String... strangers) {
        Long seller = account(sellerUsername).getId();
        Set<Long> touched = new java.util.HashSet<>();
        for (String stranger : strangers) {
            touched.addAll(payments.findInvoiceIdsWhoseCashPassedBy(account(stranger).getId()));
        }
        Invoice invoice = invoices.findAll().stream()
                .filter(candidate -> seller.equals(candidate.getSale().getSeller().getId()))
                .filter(candidate -> candidate.getPaymentStatus() == com.houssen.liberoshop.entity.PaymentStatus.PAID)
                .filter(candidate -> candidate.getDeliveryStatus()
                        == com.houssen.liberoshop.entity.DeliveryStatus.DELIVERED)
                .filter(candidate -> candidate.getHandledBy() == null)
                .filter(candidate -> !touched.contains(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no closed sale of " + sellerUsername + " in the dataset"));
        invoice.setInvoiceDate(LocalDateTime.now().minusDays(7));
        invoices.flush();
        return invoice;
    }

    private Set<Long> idsListedFor(String username) throws Exception {
        return Set.copyOf(listed("/api/invoices", username).findValuesAsString("invoiceNumber").stream()
                .map(number -> invoices.findByInvoiceNumber(number).orElseThrow().getId())
                .toList());
    }

    private JsonNode listed(String path, String username) throws Exception {
        MvcResult result = mvc.perform(get(path).header("Authorization", bearer(signIn(username))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private UserApp account(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    private String signIn(String username) throws Exception {
        return tokenOf(mvc.perform(login(username, PASSWORD)).andExpect(status().isOk()).andReturn());
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private String tokenOf(MvcResult login) throws Exception {
        return json.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
