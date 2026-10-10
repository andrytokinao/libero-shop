package com.houssen.liberoshop.license;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The renewal code exists to remove two manual steps from the offline workflow: retyping a
 * twenty character fingerprint, and looking up the expiry date a renewal must start from.
 * These tests are about the code surviving the journey through an email, a phone photo and
 * a customer's keyboard.
 */
class RenewalCodeTest {

    private static final String FINGERPRINT = "LS1-4KQ8T-9WZ2M-H7PXR-C3NVB";
    private static final LocalDate EXPIRY = LocalDate.of(2027, 9, 24);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Test
    @DisplayName("carries the fingerprint, the expiry and the day it was produced")
    void roundTripsEveryField() {
        String code = RenewalCode.encode(FINGERPRINT, RenewalCode.stateCode(LicenseState.GRACE),
                EXPIRY, TODAY, 0x0BADCAFE);

        RenewalCode.Decoded decoded = RenewalCode.decode(code);

        assertEquals(FINGERPRINT, decoded.fingerprint());
        assertEquals(RenewalCode.stateCode(LicenseState.GRACE), decoded.state());
        assertEquals(EXPIRY, decoded.currentExpiry());
        assertEquals(TODAY, decoded.requestedOn());
        assertEquals(0x0BADCAFE, decoded.nonce());
    }

    @Test
    @DisplayName("is built from a status, and a trial carries no expiry to renew from")
    void takesNoExpiryFromATrial() {
        // A trial expiry is minted locally. Letting it through would have the publisher
        // start a paid period from a date the machine invented for itself.
        LicenseStatus trial = status(LicenseState.TRIAL, LocalDate.of(2026, 12, 24));

        RenewalCode.Decoded decoded = RenewalCode.decode(RenewalCode.of(trial));

        assertNull(decoded.currentExpiry());
        assertTrue(decoded.isFirstPurchase());
        assertEquals(RenewalCode.stateCode(LicenseState.TRIAL), decoded.state());
        assertEquals(FINGERPRINT, decoded.fingerprint());
    }

    @Test
    @DisplayName("keeps the expiry of a real license, which is what --starts-on needs")
    void keepsTheExpiryOfARealLicense() {
        RenewalCode.Decoded decoded = RenewalCode.decode(RenewalCode.of(status(LicenseState.READ_ONLY, EXPIRY)));

        assertEquals(EXPIRY, decoded.currentExpiry());
        assertEquals(TODAY, decoded.requestedOn(), "the evaluation date, so a rolled-back clock cannot lie");
    }

    @Test
    @DisplayName("works for an installation whose state cannot be evaluated at all")
    void worksWithNoStatusAtAll() {
        // The moment a customer most needs to order a license is the moment the application
        // cannot work out what it has.
        RenewalCode.Decoded decoded = RenewalCode.decode(RenewalCode.ofUnlicensed(FINGERPRINT, TODAY));

        assertEquals(FINGERPRINT, decoded.fingerprint());
        assertEquals(RenewalCode.STATE_UNKNOWN, decoded.state());
        assertTrue(decoded.isFirstPurchase());
    }

    @Test
    @DisplayName("survives lower case, missing groups separators and stray spaces")
    void toleratesHowPeopleRetypeIt() {
        String code = RenewalCode.encode(FINGERPRINT, 1, EXPIRY, TODAY, 42);

        String mangled = "  " + code.toLowerCase().replace("-", " ") + " ";

        assertEquals(FINGERPRINT, RenewalCode.decode(mangled).fingerprint());
    }

    @Test
    @DisplayName("reads O as zero and I or L as one, the way Crockford intends")
    void repairsTheConfusableLetters() {
        String code = RenewalCode.encode(FINGERPRINT, 1, EXPIRY, TODAY, 42);
        // What a customer types when reading the code off a screen. The alphabet leaves
        // these letters out precisely because they get substituted, so decoding must undo
        // the substitution rather than refuse the code. The LSR1 prefix stays strict: it is
        // copied verbatim, and a wrong one deserves its own clear message.
        int afterPrefix = RenewalCode.PREFIX.length();
        String retyped = code.substring(0, afterPrefix)
                + code.substring(afterPrefix).replace('0', 'O').replace('1', 'I');

        assertEquals(EXPIRY, RenewalCode.decode(retyped).currentExpiry());
    }

    @Test
    @DisplayName("rejects a code with two characters swapped")
    void rejectsATypo() {
        String code = RenewalCode.encode(FINGERPRINT, 1, EXPIRY, TODAY, 42);
        // Swap two characters inside the first group, the classic transcription slip.
        char[] characters = code.toCharArray();
        int first = code.indexOf('-') + 1;
        char swap = characters[first];
        characters[first] = characters[first + 1];
        characters[first + 1] = swap;
        String mistyped = new String(characters);

        // Guard against the swap being a no-op on a code where those two happen to match.
        if (mistyped.equals(code)) {
            return;
        }
        assertThrows(IllegalArgumentException.class, () -> RenewalCode.decode(mistyped));
    }

    @Test
    @DisplayName("rejects a wrong final character instead of ignoring its spare bit")
    void rejectsAWrongFinalCharacter() {
        // The last character carries four real bits and one pad bit. While that pad bit was
        // free, every code had a twin that decoded to exactly the same bytes -- so a single
        // mistyped character at the end was accepted and a licence was issued from it. The
        // encoding is canonical now: only the zero-padded spelling decodes.
        String code = RenewalCode.encode(FINGERPRINT, 1, EXPIRY, TODAY, 42);
        char last = code.charAt(code.length() - 1);
        char twin = Character.isDigit(last) || "ABCDEFGHJKMNPQRSTVWXYZ".indexOf(last) >= 0
                ? flipLowBit(last)
                : last;
        String mistyped = code.substring(0, code.length() - 1) + twin;

        assertThrows(IllegalArgumentException.class, () -> RenewalCode.decode(mistyped),
                "the twin spelling '" + mistyped + "' must not decode");
    }

    /** The neighbouring alphabet character, i.e. the one differing only in the pad bit. */
    private static char flipLowBit(char c) {
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        return alphabet.charAt(alphabet.indexOf(c) ^ 1);
    }

    @Test
    @DisplayName("rejects a truncated code rather than reading half of it")
    void rejectsATruncatedCode() {
        String code = RenewalCode.encode(FINGERPRINT, 1, EXPIRY, TODAY, 42);

        assertThrows(IllegalArgumentException.class,
                () -> RenewalCode.decode(code.substring(0, code.lastIndexOf('-'))));
    }

    @Test
    @DisplayName("rejects something that is not a renewal code")
    void rejectsAnythingElse() {
        assertThrows(IllegalArgumentException.class, () -> RenewalCode.decode(FINGERPRINT));
        assertThrows(IllegalArgumentException.class, () -> RenewalCode.decode(""));
        assertThrows(IllegalArgumentException.class, () -> RenewalCode.decode(null));
    }

    @Test
    @DisplayName("stays short enough to be read out or photographed")
    void staysShort() {
        String code = RenewalCode.encode(FINGERPRINT, 1, EXPIRY, TODAY, -1);

        // Nine groups of five plus the prefix. Long enough to need copy-paste or a photo,
        // short enough that a customer can check it group by group over the phone.
        assertEquals(58, code.length(), code);
        assertTrue(code.startsWith("LSR1-"), code);
    }

    @Test
    @DisplayName("refuses a date the two-byte format cannot carry")
    void refusesAnOutOfRangeDate() {
        assertThrows(IllegalArgumentException.class,
                () -> RenewalCode.encode(FINGERPRINT, 1, LocalDate.of(2200, 1, 1), TODAY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> RenewalCode.encode(FINGERPRINT, 1, LocalDate.of(2019, 12, 31), TODAY, 0));
    }

    private static LicenseStatus status(LicenseState state, LocalDate expiresOn) {
        License license = new License("LIC-1", "CUST-0042", "Supermarche Houssen",
                state.isTrial() ? LicensePlan.TRIAL : LicensePlan.ANNUAL,
                expiresOn.minusYears(1), expiresOn, 15, List.of(FINGERPRINT), null);
        return new LicenseStatus(license, state, TODAY, FINGERPRINT);
    }
}
