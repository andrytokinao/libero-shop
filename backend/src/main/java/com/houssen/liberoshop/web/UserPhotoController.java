package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.UserPhoto;
import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.UserPhotoService;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/**
 * Profile photos, apart from {@link UserController}: that one is the administrator's alone,
 * while a photo is seen by every signed-in screen and changed by its owner too.
 */
@RestController
@RequestMapping("/api/users/{id}/photo")
public class UserPhotoController {

    private final UserPhotoService photoService;
    private final CurrentUser currentUser;

    public UserPhotoController(UserPhotoService photoService, CurrentUser currentUser) {
        this.photoService = photoService;
        this.currentUser = currentUser;
    }

    /**
     * The picture, cached by the browser for good: the screens ask for it with the account's
     * {@code photoVersion} in the URL, which changes with the photo, so a cached copy is never
     * stale. Private, because it is only served to signed-in requests.
     */
    @GetMapping
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        UserPhoto photo = photoService.find(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .body(photo.getContent());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file)
            throws IOException {
        return photoService.store(id, file.getBytes(), currentUser.require());
    }

    @DeleteMapping
    public UserResponse remove(@PathVariable Long id) {
        return photoService.remove(id, currentUser.require());
    }
}
