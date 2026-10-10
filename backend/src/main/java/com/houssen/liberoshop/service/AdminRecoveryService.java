package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * The way back in when the administrator's password is lost and no other super-admin is there
 * to hand out a new one.
 *
 * <p>Nobody is signed in, so the proof asked is of another kind: being able to read a file on
 * the server's own disk. Starting the procedure writes a code into {@code codeFile}; whoever
 * types that code back may give a super-admin account a new password. The web layer adds that
 * both steps are accepted only from the server's computer -- see {@code SetupController}. A
 * phone on the shop's Wi-Fi can do neither, and something on the machine that merely relays
 * requests cannot read the file.
 *
 * <p>The code lives in memory, for {@link #VALIDITY}, and is good once: a restart, a success or
 * {@link #MAX_ATTEMPTS} wrong guesses forget it. Only super-admin accounts are concerned -- the
 * others get their password from a super-admin, as always.
 *
 * <p>No {@code @RequiresActiveLicense}, like the rest of the accounts: the person locked out may
 * be the one who has to install the new licence.
 */
@Service
public class AdminRecoveryService {

    public static final Duration VALIDITY = Duration.ofMinutes(15);
    public static final int MAX_ATTEMPTS = 5;

    private static final int CODE_DIGITS = 8;
    private static final Logger log = LoggerFactory.getLogger(AdminRecoveryService.class);

    private final UserAppRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;
    private final Path codeFile;
    private final SecureRandom random = new SecureRandom();

    private String code;
    private LocalDateTime expiresAt;
    private int wrongGuesses;

    public AdminRecoveryService(UserAppRepository users, PasswordEncoder passwordEncoder,
                                BusinessCalendar calendar, TransactionTemplate transaction,
                                @Value("${liberoshop.security.recovery.code-file:./data/recovery-code.txt}")
                                String codeFile) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.calendar = calendar;
        this.transaction = transaction;
        this.codeFile = Path.of(codeFile);
    }

    /**
     * Draws a code and writes it, with the names of the accounts it can reopen, into the file
     * the caller is told to open. A code already out is replaced.
     *
     * @throws BusinessRuleException {@code RECOVERY_UNAVAILABLE} when the file cannot be written
     */
    public synchronized Started start() {
        List<String> admins = users.findByRoleOrderByFullNameAsc(RoleApp.SUPER_ADMIN).stream()
                .filter(UserApp::isEnabled)
                .map(UserApp::getUsername)
                .toList();
        if (admins.isEmpty()) {
            throw new BusinessRuleException("RECOVERY_NO_ADMIN",
                    "Cette installation n'a aucun compte administrateur actif a rouvrir.");
        }
        String drawn = String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d",
                random.nextInt((int) Math.pow(10, CODE_DIGITS)));
        Path target = codeFile.toAbsolutePath().normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, """
                    Libero Shop - code de secours
                    =============================

                    Code : %s
                    Valable %d minutes, une seule fois.

                    Comptes administrateur : %s

                    Tapez ce code dans la page "Mot de passe administrateur oublie",
                    sur cet ordinateur. Si vous n'avez rien demande, supprimez ce fichier
                    et prevenez le gerant.
                    """.formatted(drawn, VALIDITY.toMinutes(), String.join(", ", admins)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Code de secours impossible a ecrire dans {} ({}).", target, e.toString());
            throw new BusinessRuleException("RECOVERY_UNAVAILABLE",
                    "Le fichier du code de secours n'a pas pu etre ecrit sur cet ordinateur.");
        }
        code = drawn;
        expiresAt = calendar.now().plus(VALIDITY);
        wrongGuesses = 0;
        log.warn("Procedure de secours demarree : code ecrit dans {}.", target);
        return new Started(target.toString(), VALIDITY.toMinutes());
    }

    /**
     * Gives {@code username} a new password, against the code written by {@link #start}. Every
     * session the account had open ends, as with any password change.
     *
     * @throws BusinessRuleException {@code RECOVERY_CODE_EXPIRED}, {@code RECOVERY_CODE_WRONG},
     *                               or {@code RECOVERY_NOT_ADMIN} -- which leaves the code usable
     */
    public synchronized UserResponse reset(String typedCode, String username, String newPassword) {
        if (code == null || !calendar.now().isBefore(expiresAt)) {
            forget();
            throw new BusinessRuleException("RECOVERY_CODE_EXPIRED",
                    "Ce code n'est plus valable : demandez-en un nouveau.");
        }
        byte[] typed = (typedCode == null ? "" : typedCode.trim()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8), typed)) {
            if (++wrongGuesses >= MAX_ATTEMPTS) {
                forget();
                throw new BusinessRuleException("RECOVERY_CODE_EXPIRED",
                        "Trop d'essais : ce code est annule, demandez-en un nouveau.");
            }
            throw new BusinessRuleException("RECOVERY_CODE_WRONG",
                    "Ce code n'est pas celui du fichier. Verifiez les " + CODE_DIGITS + " chiffres.");
        }
        String handle = username.trim().toLowerCase(Locale.ROOT);
        UserResponse reopened = transaction.execute(status -> {
            UserApp user = users.findByUsername(handle)
                    .filter(found -> found.isEnabled() && found.hasRole(RoleApp.SUPER_ADMIN))
                    .orElseThrow(() -> new BusinessRuleException("RECOVERY_NOT_ADMIN",
                            "Aucun compte administrateur actif ne porte cet identifiant : "
                                    + "ils sont listes dans le fichier du code."));
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.revokeSessions();
            return UserResponse.of(users.save(user));
        });
        forget();
        log.warn("Procedure de secours : nouveau mot de passe pour le compte administrateur {}.", handle);
        return reopened;
    }

    private void forget() {
        code = null;
        expiresAt = null;
        wrongGuesses = 0;
        try {
            Files.deleteIfExists(codeFile);
        } catch (IOException e) {
            // The code is forgotten here, which is what makes the file worthless.
            log.warn("Fichier du code de secours non supprime : {} ({}).", codeFile.toAbsolutePath(), e.toString());
        }
    }

    /**
     * @param file    where the code was written, for the person at the server to open
     * @param minutes how long the code is good for
     */
    public record Started(String file, long minutes) {
    }
}
