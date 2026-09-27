package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The file to read, as text.
 *
 * <p>Text rather than an upload, because the decoding has to happen where the file is. A
 * French Excel writes its CSV in Windows-1252, and a server handed those bytes can only guess
 * -- wrongly often enough to turn "Café" into "CafÃ©" in the catalogue. The browser has the
 * file, tries UTF-8 strictly, falls back to Windows-1252 and sends characters rather than
 * bytes. What arrives here is already right or already visibly wrong on the operator's screen.
 *
 * @param content the file's whole text, header line included
 */
public record ImportPreviewRequest(@NotBlank String content) {
}
