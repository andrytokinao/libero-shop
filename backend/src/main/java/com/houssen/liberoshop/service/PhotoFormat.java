package com.houssen.liberoshop.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats a profile photo may be, told from the file's first bytes.
 *
 * <p>Never from the name or the declared content type: those are whatever the sender wrote, and
 * this content is served back to every screen as an image. Bytes that are not one of these are
 * refused, so nothing but a picture is ever stored and served under an image type.
 */
public enum PhotoFormat {

    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    /** "RIFF", four bytes of length, then "WEBP". */
    WEBP("image/webp", new byte[]{'R', 'I', 'F', 'F'}) {
        @Override
        boolean matches(byte[] content) {
            return super.matches(content) && content.length >= 12
                    && Arrays.equals(content, 8, 12, new byte[]{'W', 'E', 'B', 'P'}, 0, 4);
        }
    };

    private final String contentType;
    private final byte[] signature;

    PhotoFormat(String contentType, byte[] signature) {
        this.contentType = contentType;
        this.signature = signature;
    }

    public String contentType() {
        return contentType;
    }

    boolean matches(byte[] content) {
        return content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }

    public static Optional<PhotoFormat> detect(byte[] content) {
        return Arrays.stream(values()).filter(format -> format.matches(content)).findFirst();
    }
}
