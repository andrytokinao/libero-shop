package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * An account's profile photo, apart from the account.
 *
 * <p>Its own table so that nothing loading accounts -- a login, a list of sales and their
 * sellers -- ever drags image bytes along: they are read only by the one request that serves
 * the picture. Kept in the database rather than on disk so that the shop's backups carry the
 * photos with everything else, and a restore brings back the faces with the names.
 *
 * <p>Small by construction: the screens shrink a photo to a thumbnail before sending it, and
 * {@code UserPhotoService} refuses anything larger than {@link #MAX_BYTES}.
 */
@Entity
@Table(name = "user_photo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPhoto {

    /** A 256-pixel JPEG is around 20 KB; this leaves room for a PNG without letting a raw photo in. */
    public static final int MAX_BYTES = 512 * 1024;

    /** The account's id: one photo per account, replaced in place. */
    @Id
    private Long userId;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    /** Read from the bytes themselves, never taken from what the client claimed. */
    @Column(nullable = false, length = 32)
    private String contentType;
}
