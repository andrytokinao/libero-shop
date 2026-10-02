package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.ImportAction;
import com.houssen.liberoshop.service.ImportOutcome;

import java.math.BigDecimal;
import java.util.List;

/**
 * One line of an import file, read and confronted with the catalogue -- but not written.
 *
 * <p>Everything here is a proposal. The operator ticks, renames, corrects a price and sends
 * the lines back to be applied; nothing has touched the database at the point this is
 * produced. That is why the line carries both what the file said and what the server suggests
 * doing about it: the screen has to be able to show the difference.
 *
 * @param line        1-based line number in the file, so a complaint names the line the
 *                    operator sees in their spreadsheet
 * @param outcome     what would happen if the line were applied as proposed
 * @param action      the proposed decision, and the default state of the row's controls
 * @param existing    the catalogue product matched, or null when nothing matched
 * @param matchedOn   how it was matched -- {@code "barcode"} or {@code "name"} -- so the
 *                    screen can say why, since a name match is a guess and a barcode is not
 * @param suggestedName a free name for the "Renommer" button to start from, numbered so it
 *                    does not collide with the product already carrying the name
 * @param categoryPath the rayon as the file wrote it, kept verbatim for display
 * @param categoryId  the rayon that path resolved to, or null -- null with a non-blank
 *                    {@code categoryPath} means the rayon would be created on apply
 * @param cost        the purchase price of one unit as the file gave it, or null
 * @param selected    whether the row starts ticked; a refused line does not
 * @param notes       what the operator needs to know about this line, in French, ready to show
 */
public record ProductImportLineResponse(int line,
                                        String name,
                                        BigDecimal quantity,
                                        String unit,
                                        BigDecimal price,
                                        BigDecimal cost,
                                        String barcode,
                                        String categoryPath,
                                        Long categoryId,
                                        ImportOutcome outcome,
                                        ImportAction action,
                                        ProductRefResponse existing,
                                        String matchedOn,
                                        String suggestedName,
                                        boolean selected,
                                        List<String> notes) {
}
