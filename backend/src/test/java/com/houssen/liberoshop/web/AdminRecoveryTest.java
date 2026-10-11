package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.AdminRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The administrator's password is lost: it is replaced at the server's computer, against a code
 * read in a file of that computer, and nowhere else.
 */
@SpringBootTest(properties = "liberoshop.security.recovery.code-file=" + AdminRecoveryTest.CODE_FILE)
@AutoConfigureMockMvc
class AdminRecoveryTest {

    static final String CODE_FILE = "target/recovery-test/recovery-code.txt";

    private static final String SERVER = "127.0.0.1";
    private static final String PHONE = "192.168.1.42";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String owner;
    private String cashier;

    @BeforeEach
    void anOwnerAndACashier() throws Exception {
        Files.deleteIfExists(Path.of(CODE_FILE));
        owner = "g" + UUID.randomUUID().toString().substring(0, 8);
        cashier = "c" + UUID.randomUUID().toString().substring(0, 8);
        users.save(UserApp.builder().fullName("Gerant").username(owner)
                .password(passwordEncoder.encode("ancien-mot-de-passe")).enabled(true)
                .roles(EnumSet.of(RoleApp.SUPER_ADMIN)).build());
        users.save(UserApp.builder().fullName("Caisse").username(cashier)
                .password(passwordEncoder.encode("ancien-mot-de-passe")).enabled(true)
                .roles(EnumSet.of(RoleApp.CASHIER)).build());
    }

    @Test
    @DisplayName("Sur le serveur, avec le code du fichier : nouveau mot de passe, une seule fois")
    void recoversAtTheServer() throws Exception {
        mvc.perform(post("/api/setup/recovery/code").with(from(SERVER)))
                .andExpect(status().isOk())
                // Where the code is, the support says: the answer does not.
                .andExpect(jsonPath("$.file").doesNotExist())
                .andExpect(jsonPath("$.minutes").value(15));
        String written = Files.readString(Path.of(CODE_FILE));
        String code = codeIn(written);
        // A forgotten identifier is read there too.
        assertTrue(written.contains(owner));
        assertFalse(written.contains(cashier));

        mvc.perform(reset("00000000".equals(code) ? "11111111" : "00000000", owner, "nouveau-mot-de-passe")
                        .with(from(SERVER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_WRONG"));
        // The code reopens the administration, not the till's account...
        mvc.perform(reset(code, cashier, "nouveau-mot-de-passe").with(from(SERVER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOVERY_NOT_ADMIN"));
        // ...and not with six characters.
        mvc.perform(reset(code, owner, "court1").with(from(SERVER)))
                .andExpect(status().isBadRequest());

        mvc.perform(reset(code, " " + owner.toUpperCase() + " ", "nouveau-mot-de-passe").with(from(SERVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(owner));

        mvc.perform(login(owner, "nouveau-mot-de-passe")).andExpect(status().isOk());
        mvc.perform(login(owner, "ancien-mot-de-passe")).andExpect(status().isUnauthorized());
        // Used: the file is gone and the code opens nothing any more.
        assertFalse(Files.exists(Path.of(CODE_FILE)));
        mvc.perform(reset(code, owner, "encore-un-autre").with(from(SERVER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("Depuis un telephone ou a travers un relais : ni code, ni nouveau mot de passe")
    void refusedAwayFromTheServer() throws Exception {
        mvc.perform(post("/api/setup/recovery/code").with(from(PHONE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOVERY_ONLY_ON_SERVER"));
        assertFalse(Files.exists(Path.of(CODE_FILE)));

        mvc.perform(post("/api/setup/recovery/code").with(from(SERVER)))
                .andExpect(status().isOk());
        String code = codeIn(Files.readString(Path.of(CODE_FILE)));

        // Even with the right code, read over someone's shoulder.
        mvc.perform(reset(code, owner, "nouveau-mot-de-passe").with(from(PHONE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOVERY_ONLY_ON_SERVER"));
        mvc.perform(reset(code, owner, "nouveau-mot-de-passe").with(request -> {
                    request.setRemoteAddr(SERVER);
                    request.addHeader("X-Forwarded-For", PHONE);
                    return request;
                }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOVERY_ONLY_ON_SERVER"));
        mvc.perform(login(owner, "ancien-mot-de-passe")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cinq mauvais codes annulent le bon")
    void wrongGuessesCancelTheCode() throws Exception {
        mvc.perform(post("/api/setup/recovery/code").with(from(SERVER)))
                .andExpect(status().isOk());
        String code = codeIn(Files.readString(Path.of(CODE_FILE)));
        String wrong = "00000000".equals(code) ? "11111111" : "00000000";

        for (int guess = 1; guess < AdminRecoveryService.MAX_ATTEMPTS; guess++) {
            mvc.perform(reset(wrong, owner, "nouveau-mot-de-passe").with(from(SERVER)))
                    .andExpect(jsonPath("$.code").value("RECOVERY_CODE_WRONG"));
        }
        mvc.perform(reset(wrong, owner, "nouveau-mot-de-passe").with(from(SERVER)))
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_EXPIRED"));
        mvc.perform(reset(code, owner, "nouveau-mot-de-passe").with(from(SERVER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_EXPIRED"));
        assertFalse(Files.exists(Path.of(CODE_FILE)));
    }

    private static String codeIn(String written) {
        Matcher matcher = Pattern.compile("Code : (\\d{8})").matcher(written);
        assertTrue(matcher.find(), written);
        return matcher.group(1);
    }

    private static MockHttpServletRequestBuilder reset(String code, String username, String password) {
        return post("/api/setup/recovery/password").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"%s","username":"%s","password":"%s"}""".formatted(code, username, password));
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}""".formatted(username, password));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
