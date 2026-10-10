package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateFirstAdminRequest;
import com.houssen.liberoshop.web.dto.CreateUserRequest;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The door an empty installation leaves open, and the moment it closes: the first account is
 * a super-admin, and there is never a second "first account".
 */
@ExtendWith(MockitoExtension.class)
class InitialSetupServiceTest {

    private static final CreateFirstAdminRequest OWNER =
            new CreateFirstAdminRequest("Rivo Rakoto", "rivo", "motdepasse-long");

    @Mock
    private UserAppRepository users;
    @Mock
    private UserAccountService accounts;

    @Test
    @DisplayName("Sans aucun compte, l'installation attend son premier administrateur")
    void requiredWhileEmpty() {
        when(users.count()).thenReturn(0L);

        assertTrue(new InitialSetupService(users, accounts).isRequired());
    }

    @Test
    @DisplayName("Le premier compte est cree super admin, quel que soit ce qui est demande")
    void createsASuperAdmin() {
        when(users.count()).thenReturn(0L);
        when(accounts.create(any())).thenReturn(
                new UserResponse(1L, "Rivo Rakoto", "rivo", List.of(RoleApp.SUPER_ADMIN), true, null));

        UserResponse admin = new InitialSetupService(users, accounts).createFirstAdmin(OWNER);

        ArgumentCaptor<CreateUserRequest> created = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(accounts).create(created.capture());
        assertEquals(Set.of(RoleApp.SUPER_ADMIN), created.getValue().roles());
        assertEquals("rivo", created.getValue().username());
        assertEquals("motdepasse-long", created.getValue().password());
        assertEquals("rivo", admin.username());
    }

    @Test
    @DisplayName("Des qu'un compte existe, la porte est fermee et rien n'est cree")
    void refusedOnceAnAccountExists() {
        when(users.count()).thenReturn(1L);
        InitialSetupService setup = new InitialSetupService(users, accounts);

        assertFalse(setup.isRequired());
        BusinessRuleException refused =
                assertThrows(BusinessRuleException.class, () -> setup.createFirstAdmin(OWNER));

        assertEquals("SETUP_ALREADY_DONE", refused.code());
        verify(accounts, never()).create(any());
    }
}
