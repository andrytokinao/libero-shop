package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CashRemittanceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateUserRequest;
import com.houssen.liberoshop.web.dto.SetPasswordRequest;
import com.houssen.liberoshop.web.dto.UpdateUserRequest;
import com.houssen.liberoshop.web.dto.UserActivityResponse;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The accounts of the installation: who exists, what each of them may do, and what they did
 * today.
 *
 * <p>An account is created, renamed, given or taken roles, switched off, switched back on
 * and given a new password -- it is never deleted. Every sale, payment and hand-over is
 * signed by an account, and a shop that cannot say who sold what has lost the point of
 * keeping the trail. Disabling is the way out instead: {@code CurrentUser} re-reads the row
 * on every call and refuses a disabled account, so a leaving employee stops acting in the
 * shop's name even while the token in their browser is still cryptographically valid.
 *
 * <p>The login handle is fixed once created. The history refers to an account by its id, so
 * renaming the handle would technically break nothing -- but it is how colleagues name each
 * other, and a handle that moves makes last month's conversation about "hary" ambiguous.
 * The person's display name can be corrected freely; the handle they type cannot.
 *
 * <p>No {@code @RequiresActiveLicense} anywhere here, and that is a decision rather than an
 * oversight: an expired licence turns the shop read-only, and the person who has to install
 * the new one is exactly the one who may need a password reset to sign in and do it.
 */
@Service
@Transactional(readOnly = true)
public class UserAccountService {

    /** Short enough to be typed at a busy counter, long enough not to be a PIN. */
    public static final int MIN_PASSWORD_LENGTH = 6;
    /** BCrypt hashes the first 72 bytes and ignores the rest, so refusing longer is honest. */
    public static final int MAX_PASSWORD_LENGTH = 72;

    private final UserAppRepository users;
    private final PaymentRepository payments;
    private final CashRemittanceRepository remittances;
    private final DashboardService dashboardService;
    private final BusinessCalendar calendar;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAppRepository users, PaymentRepository payments,
                              CashRemittanceRepository remittances, DashboardService dashboardService,
                              BusinessCalendar calendar, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.payments = payments;
        this.remittances = remittances;
        this.dashboardService = dashboardService;
        this.calendar = calendar;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> findAll() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserResponse::of).toList();
    }

    public List<UserResponse> findByRole(RoleApp role) {
        return users.findByRoleOrderByFullNameAsc(role).stream().map(UserResponse::of).toList();
    }

    public List<UserActivityResponse> findActivity() {
        Map<Long, Long> salesPerSeller = dashboardService.salesToday().stream()
                .collect(Collectors.groupingBy(sale -> sale.getSeller().getId(), Collectors.counting()));

        return users.findAllByOrderByFullNameAsc().stream()
                .map(user -> {
                    List<Payment> collectedToday = payments.findByCollectorAndPeriod(
                            user.getId(), calendar.startOfToday(), calendar.startOfTomorrow());
                    return new UserActivityResponse(
                            UserResponse.of(user),
                            salesPerSeller.getOrDefault(user.getId(), 0L),
                            collectedToday.stream().map(Payment::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            // Cash taken at hand-over is what a depot agent's day amounts to.
                            // Counted for anyone who holds the role, including the owner of a
                            // small grocery who also sells at the desk.
                            user.hasRole(RoleApp.DEPOT_AGENT) ? collectedToday.size() : 0,
                            remittances.countBySubmittedById(user.getId()));
                })
                .toList();
    }

    // ----------------------------------------------------------------- write side

    /** Opens an account, enabled and ready to sign in with the password given here. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = handleOf(request.username());
        if (users.existsByUsername(username)) {
            throw new BusinessRuleException("USERNAME_TAKEN",
                    "L'identifiant " + username + " est deja utilise par un autre compte.");
        }

        return UserResponse.of(users.save(UserApp.builder()
                .fullName(request.fullName().trim())
                .username(username)
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .roles(rolesOf(request.roles()))
                .build()));
    }

    /**
     * Corrects the person's name and replaces the set of jobs the account may do.
     *
     * <p>A role change takes effect at the holder's next sign-in, not at once, and that is
     * worth knowing before relying on it: the authorities {@code @PreAuthorize} tests are
     * read off the token the person is already carrying, not off this row. A role taken away
     * here still opens its screens until that token runs out -- see
     * {@code liberoshop.security.jwt.ttl}, twelve hours by default.
     *
     * <p>So this is how duties are reorganised, not how someone is stopped. To cut an access
     * now, {@link #setEnabled} is the operation: the account's state is re-read from the row
     * on every call that acts in a user's name, so it bites on the next request.
     *
     * @param actor the signed-in super-admin doing the edit, taken from the session
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, UserApp actor) {
        UserApp user = require(id);
        Set<RoleApp> roles = rolesOf(request.roles());

        if (user.hasRole(RoleApp.SUPER_ADMIN) && !roles.contains(RoleApp.SUPER_ADMIN)) {
            if (isSelf(user, actor)) {
                throw new BusinessRuleException("SELF_DEMOTION",
                        "Vous ne pouvez pas retirer votre propre role de super admin : "
                                + "demandez-le a un autre super admin.");
            }
            refuseIfLastAdmin(user, "LAST_ADMIN_ROLE",
                    "Ce compte est le dernier super admin actif : son role ne peut pas etre retire.");
        }

        user.setFullName(request.fullName().trim());
        // Emptied and refilled rather than swapped for a new set: Hibernate follows the
        // collection instance it handed out, and replacing it makes the whole element
        // collection be deleted and re-inserted for what is often a one-role change.
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        return UserResponse.of(user);
    }

    /**
     * Revokes an account, or gives it back.
     *
     * <p>Disabling is what replaces deletion: the account stops being able to sign in or to
     * act, and everything it signed stays readable under its name.
     *
     * @param actor the signed-in super-admin, so the two ways of locking the shop out of its
     *              own administration -- switching yourself off, switching the last
     *              super-admin off -- can be refused
     */
    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled, UserApp actor) {
        UserApp user = require(id);
        if (!enabled) {
            if (isSelf(user, actor)) {
                throw new BusinessRuleException("SELF_DEACTIVATION",
                        "Vous ne pouvez pas desactiver votre propre compte.");
            }
            refuseIfLastAdmin(user, "LAST_ADMIN_DISABLED",
                    "Ce compte est le dernier super admin actif : le desactiver fermerait "
                            + "l'administration a tout le monde.");
        }
        user.setEnabled(enabled);
        return UserResponse.of(user);
    }

    /**
     * Hands a new password to an account.
     *
     * <p>A session already open survives it: the token carries no password, and nothing here
     * revokes one. Setting a password is how someone is let back in, not how they are cut
     * off -- for that, {@link #setEnabled} is the operation, and it takes effect at once.
     */
    @Transactional
    public UserResponse setPassword(Long id, SetPasswordRequest request) {
        UserApp user = require(id);
        user.setPassword(passwordEncoder.encode(request.password()));
        return UserResponse.of(user);
    }

    // -------------------------------------------------------------------- helpers

    private UserApp require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }

    private static boolean isSelf(UserApp user, UserApp actor) {
        return user.getId().equals(actor.getId());
    }

    /**
     * Refuses an edit that would leave the installation with no super-admin able to sign in.
     *
     * <p>Only the enabled ones count: a super-admin whose account is switched off cannot
     * install a licence or reopen anyone's access, so it is no answer to the lockout.
     *
     * <p>Nothing that arrives over HTTP can trip this today, and that is worth saying out
     * loud rather than leaving a reader to wonder. The actor of any write here is an enabled
     * super-admin -- the endpoint asks for the role, and {@code CurrentUser} refuses a
     * disabled account -- so as soon as the target is someone else, two of them exist and
     * the count cannot be one. Emptying the last one means emptying your own account, which
     * the two self rules above refuse first, with a message that actually helps.
     *
     * <p>It is kept all the same, because it states the invariant itself instead of deriving
     * it from who the caller happens to be. An installation with no usable super-admin can
     * no longer license itself or reopen anyone's access -- there is no way back from inside
     * the application -- and that is not a thing to protect by inference alone.
     */
    private void refuseIfLastAdmin(UserApp user, String code, String message) {
        if (!user.hasRole(RoleApp.SUPER_ADMIN) || !user.isEnabled()) {
            return;
        }
        if (users.countEnabledByRole(RoleApp.SUPER_ADMIN) <= 1) {
            throw new BusinessRuleException(code, message);
        }
    }

    /**
     * The payload also carries {@code @NotEmpty}, which answers 400 with the field named.
     * Repeated here so the rule holds whatever calls the service, and so the message reads
     * for the person at the screen rather than for the developer.
     */
    private static Set<RoleApp> rolesOf(Set<RoleApp> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new BusinessRuleException("NO_ROLE",
                    "Un compte doit avoir au moins un role : sans role, il n'aurait aucune "
                            + "page ou aller apres la connexion.");
        }
        return EnumSet.copyOf(requested);
    }

    /** Sign-in matches the handle exactly, so it is stored the one way it can be typed. */
    private static String handleOf(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
