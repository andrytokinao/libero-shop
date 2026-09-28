package com.houssen.liberoshop.license;

/**
 * The base32 alphabet shared by the machine fingerprint and the renewal code.
 *
 * <p>Both are strings a shop owner reads off a screen and someone else types back in, so
 * both use Crockford's alphabet rather than the RFC 4648 one: it omits I, L, O and U, which
 * removes the 1/I and 0/O confusion at the source, and U so that no accidental obscenity
 * appears in a code shown to a customer.
 *
 * <p>Kept in one place because the two carriers had started to diverge -- one accepted a
 * mistyped {@code O}, the other rejected it -- and the alphabet is the part that must not.
 */
final class Crockford {

    /** No I, L, O or U. Index in this array is the five-bit value. */
    static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private Crockford() {
    }

    /**
     * Maps the omitted letters onto the digits they are mistaken for, as Crockford's
     * encoding specifies for decoding.
     *
     * <p>This is the whole point of choosing the alphabet: the letters it leaves out are
     * exactly the ones a human substitutes by accident, so reading them as the intended
     * digit repairs a code that is otherwise perfectly correct. Rejecting them would turn
     * the alphabet's one benefit into an error message.
     *
     * @param upperCased a character from an already upper-cased string
     */
    static char normalise(char upperCased) {
        return switch (upperCased) {
            case 'O' -> '0';
            case 'I', 'L' -> '1';
            default -> upperCased;
        };
    }

    /** The five-bit value of one character, tolerating the confusable letters. */
    static int symbol(char c) {
        char normalised = normalise(c);
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == normalised) {
                return i;
            }
        }
        throw new IllegalArgumentException("'" + c + "' is not part of the alphabet used here "
                + "(digits plus letters, without I, L, O or U)");
    }

    /** Splits into groups of five, so a dropped group is visible rather than silent. */
    static String group(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + raw.length() / 5);
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && i % 5 == 0) {
                out.append('-');
            }
            out.append(raw.charAt(i));
        }
        return out.toString();
    }
}
