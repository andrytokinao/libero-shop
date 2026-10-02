package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.exception.BusinessRuleException;

/**
 * What an uploaded picture must be, whatever it pictures: not empty, not heavy, and really an
 * image -- read from its first bytes, never from the name or the type the browser claims.
 *
 * <p>Shared by the profile photos and the rayon images, so both refuse the same files in the
 * same words and with the same codes.
 */
public final class ImageUpload {

    private ImageUpload() {
    }

    /**
     * @param subject what the picture is, as a sentence starts: "La photo", "L'image"
     * @return the format the bytes are in
     * @throws BusinessRuleException {@code PHOTO_EMPTY}, {@code PHOTO_TOO_LARGE} or {@code PHOTO_FORMAT}
     */
    public static PhotoFormat check(byte[] content, int maxBytes, String subject) {
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("PHOTO_EMPTY", "Aucune image recue.");
        }
        if (content.length > maxBytes) {
            throw new BusinessRuleException("PHOTO_TOO_LARGE",
                    subject + " est trop lourde (" + content.length / 1024 + " Ko, au plus "
                            + maxBytes / 1024 + " Ko).");
        }
        return PhotoFormat.detect(content).orElseThrow(() -> new BusinessRuleException(
                "PHOTO_FORMAT", "Ce fichier n'est pas une image JPEG, PNG ou WebP."));
    }
}
