package com.houssen.liberoshop.printer.service.tiket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;

/**
 * Donne un {@link EscPosWriter} réglé pour le papier et l'imprimante de la boutique.
 *
 * <p>Les valeurs par défaut sont celles d'une imprimante de caisse 80 mm courante ; la bonne
 * table de caractères se trouve avec {@code PrintService.testCodePages}, qui imprime les accents
 * dans chacune :
 * <ul>
 *   <li>{@code liberoshop.printer.ticket-columns} : caractères par ligne, 48 (80 mm, police A) ou 42 ;
 *       32 pour du papier 58 mm ;</li>
 *   <li>{@code liberoshop.printer.ticket-code-page} : le numéro envoyé à l'imprimante ({@code ESC t n}),
 *       19 pour CP858 sur la plupart des modèles ;</li>
 *   <li>{@code liberoshop.printer.ticket-charset} : le même jeu de caractères côté Java, {@code Cp858}.
 *       Il vient du module {@code jdk.charsets} : un exécutable fabriqué avec jpackage doit
 *       l'inclure ({@code --add-modules jdk.charsets}).</li>
 * </ul>
 */
@Component
public class EscPosWriterFactory {

    private static final Logger log = LoggerFactory.getLogger(EscPosWriterFactory.class);

    private final Charset charset;
    private final boolean ascii;
    private final int codePage;
    private final int columns;

    public EscPosWriterFactory(@Value("${liberoshop.printer.ticket-charset:Cp858}") String charsetName,
                               @Value("${liberoshop.printer.ticket-code-page:19}") int codePage,
                               @Value("${liberoshop.printer.ticket-columns:48}") int columns) {
        Charset found = null;
        try {
            found = Charset.forName(charsetName);
        } catch (IllegalArgumentException e) {
            log.warn("Jeu de caractères « {} » introuvable : les tickets sont écrits sans accents", charsetName);
        }
        this.charset = found == null ? Charset.forName("US-ASCII") : found;
        this.ascii = found == null;
        this.codePage = codePage;
        this.columns = columns;
    }

    public EscPosWriter newWriter() {
        return new EscPosWriter(charset, ascii, codePage, columns);
    }
}