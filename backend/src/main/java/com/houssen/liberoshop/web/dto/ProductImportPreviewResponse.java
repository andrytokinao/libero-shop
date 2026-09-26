package com.houssen.liberoshop.web.dto;

import java.util.List;

/**
 * A whole import file, read and not yet applied.
 *
 * <p>Sent in one piece rather than page by page, and paginated in the browser. Two reasons,
 * both about what a preview is for: the operator ticks rows across pages and then applies the
 * lot, so a server-side page would have to hold the ticks somewhere between requests -- a
 * session this application does not keep -- and the totals at the top of the dialog ("12
 * nouveaux, 40 a fusionner") are the whole point of looking, and cannot be counted from one
 * page. {@code CsvTable.MAX_ROWS} is what keeps the piece a reasonable size.
 *
 * @param separator      the character the file turned out to use, so the dialog can say so
 *                       when a badly-separated file produces one nonsense column
 * @param recognised     the columns that were found, under the header the file actually wrote
 * @param ignored        headers no recommended column matched; kept so the operator can see a
 *                       misspelled "quantite" was skipped rather than read as zero
 * @param missing        recommended columns the file has none of, named as the import
 *                       documentation names them
 * @param createdRayons  rayon paths the file names that the shop does not have yet; applying
 *                       the import creates them
 */
public record ProductImportPreviewResponse(String separator,
                                           int total,
                                           int created,
                                           int merged,
                                           int renamed,
                                           int skipped,
                                           int withoutCategory,
                                           List<String> recognised,
                                           List<String> ignored,
                                           List<String> missing,
                                           List<String> createdRayons,
                                           List<ProductImportLineResponse> lines) {
}
