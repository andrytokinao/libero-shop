package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.entity.UserPhoto;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.repository.UserPhotoRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Profile photos: stored, served, removed.
 *
 * <p>Anyone signed in may see a colleague's photo -- that is the point of it, in a list of sales
 * or on the slip a cashier confirms. Only the person themself, or the administrator, may change
 * or remove it.
 *
 * <p>No {@code @RequiresActiveLicense}, for the reason {@link UserAccountService} gives: a photo
 * is part of the account, not of the shop's trading, and an expired licence must not freeze it.
 */
@Service
@Transactional(readOnly = true)
public class UserPhotoService {

    private final UserPhotoRepository photos;
    private final UserAppRepository users;
    private final Clock clock;

    public UserPhotoService(UserPhotoRepository photos, UserAppRepository users, Clock clock) {
        this.photos = photos;
        this.users = users;
        this.clock = clock;
    }

    /** The photo as stored, with the type its bytes were recognised as. */
    public UserPhoto find(Long userId) {
        return photos.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune photo pour ce compte."));
    }

    /**
     * Sets, or replaces, an account's photo.
     *
     * @throws BusinessRuleException when the bytes are not a JPEG, PNG or WebP image, or too big
     * @throws AccessDeniedException when {@code actor} is neither the account nor an administrator
     */
    @Transactional
    public UserResponse store(Long userId, byte[] content, UserApp actor) {
        UserApp user = editableBy(userId, actor);
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("PHOTO_EMPTY", "Aucune image recue.");
        }
        if (content.length > UserPhoto.MAX_BYTES) {
            throw new BusinessRuleException("PHOTO_TOO_LARGE",
                    "La photo est trop lourde (" + content.length / 1024 + " Ko, au plus "
                            + UserPhoto.MAX_BYTES / 1024 + " Ko).");
        }
        PhotoFormat format = PhotoFormat.detect(content).orElseThrow(() -> new BusinessRuleException(
                "PHOTO_FORMAT", "Ce fichier n'est pas une image JPEG, PNG ou WebP."));

        photos.save(UserPhoto.builder()
                .userId(user.getId())
                .content(content)
                .contentType(format.contentType())
                .build());
        user.setPhotoVersion(clock.millis());
        return UserResponse.of(user);
    }

    /** Back to initials. Nothing to do when there was no photo. */
    @Transactional
    public UserResponse remove(Long userId, UserApp actor) {
        UserApp user = editableBy(userId, actor);
        photos.findById(user.getId()).ifPresent(photos::delete);
        user.setPhotoVersion(null);
        return UserResponse.of(user);
    }

    private UserApp editableBy(Long userId, UserApp actor) {
        if (!actor.getId().equals(userId) && !actor.hasRole(RoleApp.SUPER_ADMIN)) {
            throw new AccessDeniedException("Seul l'interesse ou l'administrateur change cette photo.");
        }
        return users.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("Compte", userId));
    }
}
