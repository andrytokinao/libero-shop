package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CashRemittanceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateUserRequest;
import com.houssen.liberoshop.web.dto.ChangePasswordRequest;
import com.houssen.liberoshop.web.dto.SetPasswordRequest;
import com.houssen.liberoshop.web.dto.UpdateUserRequest;
import com.houssen.liberoshop.web.dto.UserActivityResponse;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The accounts of the installation: who exists, what each of them may do, and what they did
 * today.
 *
 * <p>An account is created, renamed, given or taken roles, switched off, switched back on
 * and given a new password -- it is never deleted. Every sale, payment and hand-over is
 * signed by an account, and a shop that cannot say who sold what has lost the point of
 * keeping the trail. Disabling is the way out instead: the account is re-read on every
 * request and a disabled one is refused, so a leaving employee stops acting in the shop's
 * name -- and reading its figures -- even while the token in their browser is still
 * cryptographically valid.
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

    /**
     * Eight, for every account: six could be read over a shoulder at the counter and guessed
     * from a birthday. Asked of the passwords chosen from now on -- the ones already in use
     * still sign in.
     */
    public static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt hashes the first 72 bytes and ignores the rest, so refusing longer is honest. */
    public static final int MAX_PASSWORD_LENGTH = 72;

    private final UserAppRepository users;
    private final PaymentRepository payments;
    private final CashRemittanceRepository remittances;
    private final DashboardService dashboardService;
    private final BusinessCalendar calendar;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public UserAccountService(UserAppRepository users, PaymentRepository payments,
                              CashRemittanceRepository remittances, DashboardService dashboardService,
                              BusinessCalendar calendar, PasswordEncoder passwordEncoder,
                              ApplicationEventPublisher events) {
        this.events = events;
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

    /**
     * Each account's day, in four grouped queries whatever the number of accounts -- not two
     * queries per account, as the list of users grows.
     */
    public List<UserActivityResponse> findActivity() {
        Map<Long, Long> salesPerSeller = countsById(dashboardService.salesTodayBySeller());
        Map<Long, Object[]> collectedPerUser = new HashMap<>();
        for (Object[] row : payments.sumCollectedByCollector(calendar.startOfToday(), calendar.startOfTomorrow())) {
            collectedPerUser.put((Long) row[0], row);
        }
        Map<Long, Long> slipsPerUser = countsById(remittances.countBySubmitter());

        return users.findAllByOrderByFullNameAsc().stream()
                .map(user -> {
                    Object[] collected = collectedPerUser.get(user.getId());
                    long collectedCount = collected == null ? 0 : ((Number) collected[1]).longValue();
                    BigDecimal collectedAmount = collected == null ? BigDecimal.ZERO : (BigDecimal) collected[2];
                    return new UserActivityResponse(
                            UserResponse.of(user),
                            salesPerSeller.getOrDefault(user.getId(), 0L),
                            collectedAmount,
                            // Cash taken at hand-over is what a depot agent's day amounts to.
                            // Counted for anyone who holds the role, including the owner of a
                            // small grocery who also sells at the desk.
                            user.hasRole(RoleApp.DEPOT_AGENT) ? collectedCount : 0,
                            slipsPerUser.getOrDefault(user.getId(), 0L));
                })
                .toList();
    }

    /** {@code [id, count]} rows as a map. */
    private static Map<Long, Long> countsById(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put((Long) row[0], ((Number) row[1]).longValue()));
        return counts;
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
     * <p>A role change takes effect on the holder's next request: the authorities
     * {@code @PreAuthorize} tests are read from this row each time, not from the token the
     * person is carrying. Their screen follows at its next reload -- until then a menu entry
     * may still show, and answer "votre role ne vous autorise pas".
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
        boolean rolesChanged = !user.getRoles().equals(roles);
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        if (rolesChanged) {
            // The connections it has open were told its old roles: they are closed, and come back
            // with the new ones.
            events.publishEvent(new AccountAccessChangedEvent(user.getId()));
        }
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
        if (!enabled) {
            events.publishEvent(new AccountAccessChangedEvent(user.getId()));
        }
        return UserResponse.of(user);
    }

    /**
     * Hands a new password to an account.
     *
     * <p>Every session the account has open ends with it: a password is changed because the
     * old one is known to someone it should not be, and whoever signed in with it must not
     * stay in. The holder signs in again with the new one -- including the super-admin who
     * changes their own.
     */
    @Transactional
    public UserResponse setPassword(Long id, SetPasswordRequest request, UserApp actor) {
        UserApp user = require(id);
        user.setPassword(passwordEncoder.encode(request.password()));
        // Chosen by someone else: the holder replaces it at their next sign-in.
        user.setMustChangePassword(!isSelf(user, actor));
        user.revokeSessions();
        events.publishEvent(new AccountAccessChangedEvent(user.getId()));
        return UserResponse.of(user);
    }

    /**
     * An account opened by an administrator for someone else: its holder is asked for a
     * password of their own at their first sign-in, so the administrator stops knowing it.
     */
    @Transactional
    public UserResponse createForSomeone(CreateUserRequest request) {
        UserResponse created = create(request);
        require(created.id()).setMustChangePassword(true);
        return created;
    }

    /**
     * The holder replaces their own password, proving they know the current one: a screen left
     * open on someone's session does not let a passer-by lock them out.
     *
     * <p>Every session of the account ends, this one included -- the screen signs in again with
     * the new password.
     *
     * @throws BusinessRuleException {@code WRONG_PASSWORD}, or {@code SAME_PASSWORD} when the new
     *                               one is the one being replaced
     */
    @Transactional
    public void changeOwnPassword(UserApp actor, ChangePasswordRequest request) {
        UserApp user = require(actor.getId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("WRONG_PASSWORD", "Le mot de passe actuel n'est pas le bon.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessRuleException("SAME_PASSWORD",
                    "Le nouveau mot de passe doit etre different de l'actuel.");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.revokeSessions();
        events.publishEvent(new AccountAccessChangedEvent(user.getId()));
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
