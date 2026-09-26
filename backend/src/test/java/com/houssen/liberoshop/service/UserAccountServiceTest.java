package com.houssen.liberoshop.service;

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
import com.houssen.liberoshop.web.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules that stand between a super-admin and a shop locked out of its own
 * administration, plus the handling of the one secret this service touches.
 *
 * <p>The password encoder is the real BCrypt one rather than a mock: what is worth asserting
 * is that the clear password does not reach the column, and a stubbed encoder would be
 * asserting the stub.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAccountServiceTest {

    @Mock
    private UserAppRepository users;
    @Mock
    private PaymentRepository payments;
    @Mock
    private CashRemittanceRepository remittances;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private BusinessCalendar calendar;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(users, payments, remittances, dashboardService, calendar,
                passwordEncoder);
        // Saving hands the entity back, as the repository would once it had an id.
        when(users.save(any(UserApp.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ------------------------------------------------------------------- creation

    @Test
    @DisplayName("stores the login handle in lower case, because sign-in matches it exactly")
    void lowerCasesTheHandle() {
        UserResponse created = service.create(new CreateUserRequest(
                "  Fatima Randria  ", "Fatima", "motdepasse", EnumSet.of(RoleApp.CASHIER)));

        assertEquals("fatima", created.username());
        assertEquals("Fatima Randria", created.fullName());
    }

    @Test
    @DisplayName("hashes the password instead of storing what was typed")
    void hashesThePassword() {
        service.create(new CreateUserRequest("Hary Rakoto", "hary", "motdepasse",
                EnumSet.of(RoleApp.CASHIER)));

        UserApp saved = savedUser();
        assertNotEquals("motdepasse", saved.getPassword());
        assertTrue(passwordEncoder.matches("motdepasse", saved.getPassword()));
    }

    @Test
    @DisplayName("refuses a handle another account already answers to")
    void refusesATakenHandle() {
        when(users.existsByUsername("hary")).thenReturn(true);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.create(new CreateUserRequest("Hary Bis", "HARY", "motdepasse",
                        EnumSet.of(RoleApp.CASHIER))));
        assertEquals("USERNAME_TAKEN", refused.code());
    }

    @Test
    @DisplayName("refuses an account with no role, which would have nowhere to land")
    void refusesAnAccountWithoutRole() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.create(new CreateUserRequest("Sans Role", "sansrole", "motdepasse",
                        Set.of())));
        assertEquals("NO_ROLE", refused.code());
    }

    // ---------------------------------------------------------------------- roles

    /**
     * The invariant on its own, not a scenario the API can produce: a caller who reaches the
     * write side is an enabled super-admin, so a target other than themselves means two of
     * them exist. The count is stubbed to one to check the guard behind that reasoning still
     * holds -- see {@code refuseIfLastAdmin} for why it is kept.
     */
    @Test
    @DisplayName("refuses to take the super-admin role off the last one who can sign in")
    void keepsOneAdminWhenChangingRoles() {
        UserApp lastAdmin = account(1L, "mparany", true, RoleApp.SUPER_ADMIN);
        UserApp otherAdmin = account(2L, "soa", true, RoleApp.SUPER_ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(lastAdmin));
        when(users.countEnabledByRole(RoleApp.SUPER_ADMIN)).thenReturn(1L);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.update(1L, new UpdateUserRequest("Mparany Solofo",
                        EnumSet.of(RoleApp.CASHIER)), otherAdmin));
        assertEquals("LAST_ADMIN_ROLE", refused.code());
        assertTrue(lastAdmin.hasRole(RoleApp.SUPER_ADMIN));
    }

    @Test
    @DisplayName("refuses a super-admin taking away their own role, even when another remains")
    void refusesSelfDemotion() {
        UserApp admin = account(1L, "mparany", true, RoleApp.SUPER_ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.countEnabledByRole(RoleApp.SUPER_ADMIN)).thenReturn(2L);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.update(1L, new UpdateUserRequest("Mparany Solofo",
                        EnumSet.of(RoleApp.CASHIER)), admin));
        assertEquals("SELF_DEMOTION", refused.code());
    }

    @Test
    @DisplayName("replaces the whole set of roles, and renames the person")
    void replacesTheRoles() {
        UserApp soa = account(3L, "soa", true, RoleApp.CASHIER, RoleApp.DEPOT_AGENT);
        when(users.findById(3L)).thenReturn(Optional.of(soa));

        UserResponse updated = service.update(3L,
                new UpdateUserRequest(" Soa Ravelo ", EnumSet.of(RoleApp.DEPOT_MANAGER)),
                account(1L, "mparany", true, RoleApp.SUPER_ADMIN));

        assertEquals("Soa Ravelo", updated.fullName());
        assertEquals(EnumSet.of(RoleApp.DEPOT_MANAGER), soa.getRoles());
        // The handle is not the administrator's to change, whatever the payload carries.
        assertEquals("soa", updated.username());
    }

    // ------------------------------------------------------------- enable/disable

    @Test
    @DisplayName("refuses to switch off the account of the super-admin doing the switching")
    void refusesSelfDeactivation() {
        UserApp admin = account(1L, "mparany", true, RoleApp.SUPER_ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.countEnabledByRole(RoleApp.SUPER_ADMIN)).thenReturn(2L);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.setEnabled(1L, false, admin));
        assertEquals("SELF_DEACTIVATION", refused.code());
        assertTrue(admin.isEnabled());
    }

    /** The same invariant on the disable path, and reachable no more than the one above. */
    @Test
    @DisplayName("refuses to switch off the last super-admin who can still sign in")
    void keepsOneAdminEnabled() {
        UserApp lastAdmin = account(1L, "mparany", true, RoleApp.SUPER_ADMIN);
        UserApp otherAdmin = account(2L, "soa", true, RoleApp.SUPER_ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(lastAdmin));
        when(users.countEnabledByRole(RoleApp.SUPER_ADMIN)).thenReturn(1L);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.setEnabled(1L, false, otherAdmin));
        assertEquals("LAST_ADMIN_DISABLED", refused.code());
    }

    @Test
    @DisplayName("switches an ordinary account off and back on")
    void revokesAndRestores() {
        UserApp fatima = account(4L, "fatima", true, RoleApp.CASHIER);
        UserApp admin = account(1L, "mparany", true, RoleApp.SUPER_ADMIN);
        when(users.findById(4L)).thenReturn(Optional.of(fatima));

        assertFalse(service.setEnabled(4L, false, admin).enabled());
        assertTrue(service.setEnabled(4L, true, admin).enabled());
    }

    @Test
    @DisplayName("reports an unknown account as missing rather than failing later")
    void refusesAnUnknownAccount() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.setPassword(99L, new SetPasswordRequest("motdepasse")));
    }

    // ------------------------------------------------------------------- password

    @Test
    @DisplayName("replaces the hash and leaves the roles and the state alone")
    void setsANewPassword() {
        UserApp fatima = account(4L, "fatima", true, RoleApp.CASHIER);
        String before = fatima.getPassword();
        when(users.findById(4L)).thenReturn(Optional.of(fatima));

        service.setPassword(4L, new SetPasswordRequest("nouveaumotdepasse"));

        assertNotEquals(before, fatima.getPassword());
        assertTrue(passwordEncoder.matches("nouveaumotdepasse", fatima.getPassword()));
        assertEquals(EnumSet.of(RoleApp.CASHIER), fatima.getRoles());
        assertTrue(fatima.isEnabled());
    }

    // -------------------------------------------------------------------- fixture

    private UserApp account(Long id, String username, boolean enabled, RoleApp... roles) {
        return UserApp.builder()
                .id(id)
                .fullName(username)
                .username(username)
                .password(passwordEncoder.encode("ancienmotdepasse"))
                .enabled(enabled)
                .roles(EnumSet.copyOf(Set.of(roles)))
                .build();
    }

    private UserApp savedUser() {
        ArgumentCaptor<UserApp> captor = ArgumentCaptor.forClass(UserApp.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }
}
