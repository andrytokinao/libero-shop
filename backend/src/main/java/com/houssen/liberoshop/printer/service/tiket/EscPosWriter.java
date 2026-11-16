package com.houssen.liberoshop.printer.service.tiket;

import com.houssen.liberoshop.entity.ShopInfo;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Écrit un ticket en commandes ESC/POS : du texte à la largeur du papier, centré ou aligné à
 * gauche, en gras ou en grand, avec des lignes à deux colonnes pour les montants.
 *
 * <p>Construit par {@link EscPosWriterFactory}, qui connaît la largeur du papier et la table de
 * caractères de l'imprimante. Les accents n'arrivent à l'écran de l'imprimante que si cette
 * table est la bonne ; faute de table connue, le texte est écrit sans accents plutôt qu'avec
 * des caractères faux.
 */
public final class EscPosWriter {

    public enum Align { LEFT, CENTER, RIGHT }

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final Charset charset;
    private final boolean ascii;
    private final int columns;

    EscPosWriter(Charset charset, boolean ascii, int codePage, int columns) {
        this.charset = charset;
        this.ascii = ascii;
        this.columns = columns;
        raw(0x1B, 0x40);                       // ESC @ : remet l'imprimante à zéro
        if (!ascii) {
            raw(0x1B, 0x74, codePage);         // ESC t n : table de caractères
        }
    }

    public int columns() {
        return columns;
    }

    public EscPosWriter align(Align align) {
        raw(0x1B, 0x61, align.ordinal());      // ESC a n
        return this;
    }

    public EscPosWriter bold(boolean on) {
        raw(0x1B, 0x45, on ? 1 : 0);           // ESC E n
        return this;
    }

    /** Double hauteur, même largeur : le nombre de colonnes ne change pas. */
    public EscPosWriter tall(boolean on) {
        raw(0x1D, 0x21, on ? 0x01 : 0x00);     // GS ! n
        return this;
    }

    /** Le texte, passé à la ligne aux mots si besoin, chaque ligne terminée. */
    public EscPosWriter line(String text) {
        for (String part : wrap(text)) {
            out.writeBytes(encode(part));
            raw(0x0A);
        }
        return this;
    }

    /** {@code left} à gauche, {@code right} collé à droite ; sur deux lignes si les deux ne tiennent pas. */
    public EscPosWriter row(String left, String right) {
        String l = clean(left);
        String r = clean(right);
        if (l.length() + 1 + r.length() <= columns) {
            out.writeBytes(encode(l + " ".repeat(columns - l.length() - r.length()) + r));
            raw(0x0A);
        } else {
            line(l);
            out.writeBytes(encode(" ".repeat(Math.max(0, columns - r.length())) + r));
            raw(0x0A);
        }
        return this;
    }

    public EscPosWriter rule() {
        return line("-".repeat(columns));
    }

    /** Le nom de la boutique en grand, centré, puis son adresse et son contact s'ils existent. */
    public EscPosWriter shopHeader(ShopInfo shop) {
        align(Align.CENTER).bold(true).tall(true).line(shop.name()).tall(false).bold(false);
        if (!shop.address().isEmpty()) {
            line(shop.address());
        }
        if (!shop.contact().isEmpty()) {
            line(shop.contact());
        }
        rule();
        return align(Align.LEFT);
    }

    public EscPosWriter feed(int lines) {
        raw(0x1B, 0x64, lines);                // ESC d n
        return this;
    }

    /** Avance jusqu'au couteau, puis coupe (coupe partielle). Sans couteau, la commande est ignorée. */
    public EscPosWriter cut() {
        raw(0x1D, 0x56, 0x42, 0x00);           // GS V B 0
        return this;
    }

    public byte[] bytes() {
        return out.toByteArray();
    }

    private void raw(int... values) {
        for (int value : values) {
            out.write(value);
        }
    }

    private byte[] encode(String text) {
        return text.getBytes(charset);         // un caractère inconnu de la table devient « ? »
    }

    /** Espaces insécables en espaces ; sans table de caractères, accents retirés. */
    private String clean(String text) {
        String cleaned = (text == null ? "" : text).replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2009', ' ');
        if (ascii) {
            cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        }
        return cleaned;
    }

    private List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : clean(text).split(" ")) {
            while (word.length() > columns) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, columns));
                word = word.substring(columns);
            }
            if (current.length() > 0 && current.length() + 1 + word.length() > columns) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0 || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}