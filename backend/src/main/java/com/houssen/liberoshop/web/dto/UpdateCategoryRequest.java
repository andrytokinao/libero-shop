package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A rayon as it should now read: its name, and where it hangs.
 *
 * <p>A PUT, so both fields are always meant: {@code parentId} at null moves the rayon out to
 * the top level rather than leaving it where it was. The alternative -- null meaning "do not
 * touch" -- would leave no way at all to promote a child to a root.
 */
public record UpdateCategoryRequest(@NotBlank @Size(max = 60) String name, Long parentId) {
}
