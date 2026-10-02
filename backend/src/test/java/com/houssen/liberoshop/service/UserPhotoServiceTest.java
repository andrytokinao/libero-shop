package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Profile photos: who may change them, what is accepted, and the version the screens follow. */
@SpringBootTest
class UserPhotoServiceTest {

    /** The eight-byte PNG signature followed by a little filler: enough to be recognised. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    @Autowired
    private UserPhotoService service;
    @Autowired
    private UserAppRepository users;

    private UserApp account(RoleApp... roles) {
        String handle = "p" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Compte " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.copyOf(List.of(roles))).build());
    }

    @Test
    @DisplayName("a person sets their own photo; the type comes from the bytes and the version moves")
    void storesOwnPhoto() {
        UserApp hary = account(RoleApp.CASHIER);

        UserResponse first = service.store(hary.getId(), PNG, hary);
        assertNotNull(first.photoVersion());
        assertEquals("image/png", service.find(hary.getId()).getContentType());
        assertArrayEquals(PNG, service.find(hary.getId()).getContent());

        UserResponse replaced = service.store(hary.getId(), JPEG, hary);
        assertEquals("image/jpeg", service.find(hary.getId()).getContentType(), "replaced in place");
        assertNotNull(replaced.photoVersion());
    }

    @Test
    @DisplayName("the administrator may change anyone's photo; a colleague may not")
    void onlyOwnerOrAdmin() {
        UserApp hary = account(RoleApp.CASHIER);
        UserApp joseph = account(RoleApp.DEPOT_AGENT);
        UserApp admin = account(RoleApp.SUPER_ADMIN);

        assertThrows(AccessDeniedException.class, () -> service.store(hary.getId(), PNG, joseph));
        assertNotNull(service.store(hary.getId(), PNG, admin).photoVersion());
        assertThrows(AccessDeniedException.class, () -> service.remove(hary.getId(), joseph));
    }

    @Test
    @DisplayName("anything that is not a small JPEG, PNG or WebP image is refused")
    void refusesWhatIsNotAPhoto() {
        UserApp hary = account(RoleApp.CASHIER);

        BusinessRuleException text = assertThrows(BusinessRuleException.class,
                () -> service.store(hary.getId(), "<script>alert(1)</script>".getBytes(), hary));
        assertEquals("PHOTO_FORMAT", text.code());

        byte[] huge = Arrays.copyOf(PNG, 600 * 1024);
        assertEquals("PHOTO_TOO_LARGE",
                assertThrows(BusinessRuleException.class, () -> service.store(hary.getId(), huge, hary)).code());
        assertEquals("PHOTO_EMPTY",
                assertThrows(BusinessRuleException.class, () -> service.store(hary.getId(), new byte[0], hary)).code());
    }

    @Test
    @DisplayName("removing brings back the initials: no version, no picture")
    void removes() {
        UserApp hary = account(RoleApp.CASHIER);
        service.store(hary.getId(), PNG, hary);

        assertNull(service.remove(hary.getId(), hary).photoVersion());
        assertThrows(ResourceNotFoundException.class, () -> service.find(hary.getId()));
        assertNull(service.remove(hary.getId(), hary).photoVersion(), "removing twice is harmless");
    }
}
