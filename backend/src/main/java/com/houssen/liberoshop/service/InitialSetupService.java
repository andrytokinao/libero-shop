package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.CreateUserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;

/**
 * How an installation with no account gets its first one: the server opens a super-admin
 * account itself, with a password it draws, and the login page shows both until that password
 * has been replaced.
 *
 * <p>A shipped installation starts empty -- the demonstration accounts, whose password is
 * printed in the documentation, are a developer's convenience and off by default. So nobody
 * could sign in. The account opened here is marked as holding a password someone else chose:
 * the screens let its holder do nothing before a password of their own is set, exactly as for
 * an account an administrator opens for an employee.
 *
 * <p>The password is drawn per installation, never a fixed one: a default printed in a manual
 * is the first thing tried on every shop's Wi-Fi. It is kept in {@code passwordFile} so that it
 * can still be shown after a restart, and only to someone at the server's computer -- see
 * {@code SetupController}. Once replaced, the file is deleted and nothing is shown any more.
 */
@Service
public class InitialSetupService {

    /** The account that opens everything gets more than the counter's six characters. */
    public static final int MIN_ADMIN_PASSWORD_LENGTH = 8;

    public static final String USERNAME = "admin";

    private static final String FULL_NAME = "Administrateur";
    private static final int PASSWORD_LENGTH = 10;
    /** Read on a screen and typed by hand: no 0/o, 1/l/i. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final Logger log = LoggerFactory.getLogger(InitialSetupService.class);

    private final UserAppRepository users;
    private final UserAccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final Path passwordFile;
    private final SecureRandom random = new SecureRandom();

    public InitialSetupService(UserAppRepository users, UserAccountService accounts,
                               PasswordEncoder passwordEncoder,
                               @Value("${liberoshop.security.first-password-file:./data/first-password.txt}")
                               String passwordFile) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordFile = Path.of(passwordFile);
    }

    /**
     * Opens the first account when there is none. After the runners, so that a configuration
     * asking for the demonstration accounts gets those and nothing else.
     *
     * <p>The password is written down before the account exists: the other way round, a disk
     * that refuses the file would leave an administrator nobody can sign in as.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void openFirstAccount() {
        if (users.count() > 0) {
            return;
        }
        String password = draw();
        Path target = passwordFile.toAbsolutePath().normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, password, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Mot de passe de depart impossible a ecrire dans {} ({}) : aucun compte cree.",
                    target, e.toString());
            return;
        }
        accounts.createForSomeone(
                new CreateUserRequest(FULL_NAME, USERNAME, password, Set.of(RoleApp.SUPER_ADMIN)));
        log.warn("Installation neuve : compte administrateur {} cree. Son mot de passe de depart est "
                + "affiche sur la page de connexion, sur cet ordinateur.", USERNAME);
    }

    /**
     * The first account and its password, for as long as that password is the one in service.
     *
     * <p>Checked against the account each time, not remembered: replaced by its holder, given
     * anew by another administrator or found again through the recovery procedure, it is no
     * longer the one in the file -- which is then deleted.
     */
    public synchronized Optional<FirstPassword> firstPassword() {
        if (!Files.isRegularFile(passwordFile)) {
            return Optional.empty();
        }
        String password;
        try {
            password = Files.readString(passwordFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.warn("Mot de passe de depart illisible dans {} ({}).", passwordFile.toAbsolutePath(), e.toString());
            return Optional.empty();
        }
        boolean inService = users.findByUsername(USERNAME)
                .filter(UserApp::isEnabled)
                .filter(UserApp::isMustChangePassword)
                .filter(admin -> passwordEncoder.matches(password, admin.getPassword()))
                .isPresent();
        if (!inService) {
            forget();
            return Optional.empty();
        }
        return Optional.of(new FirstPassword(USERNAME, password));
    }

    private void forget() {
        try {
            Files.deleteIfExists(passwordFile);
        } catch (IOException e) {
            // It opens nothing any more, which is what makes the file worthless.
            log.warn("Fichier du mot de passe de depart non supprime : {} ({}).",
                    passwordFile.toAbsolutePath(), e.toString());
        }
    }

    private String draw() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }

    public record FirstPassword(String username, String password) {
    }
}
