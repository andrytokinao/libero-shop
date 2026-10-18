package com.houssen.liberoshop.license;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The latest date seen survives the loss of a copy, a forged copy and a clock held still. */
class ClockGuardTest {

    private static final byte[] ANCHOR = "build-key".getBytes(StandardCharsets.UTF_8);
    private static final String FINGERPRINT = "MACHINE-1";
    private static final LocalDate JUNE_10 = LocalDate.of(2026, 6, 10);

    /** One copy held in memory; can be told to refuse every write. */
    private static final class MemoryStore implements TrialRegistry.TrialStore {
        String content;
        boolean refuses;

        @Override
        public String describe() {
            return "memory";
        }

        @Override
        public String read() {
            return content;
        }

        @Override
        public boolean write(String record) {
            if (refuses) {
                return false;
            }
            content = record;
            return true;
        }
    }

    private final AtomicLong nanos = new AtomicLong();

    private ClockGuard guard(MemoryStore... stores) {
        return new ClockGuard(true, List.of(stores), ANCHOR, FINGERPRINT, nanos::get);
    }

    @Test
    @DisplayName("fichier efface : la base se souvient de la date haute et le fichier est refait")
    void fichierEffaceLaBaseSeSouvient() {
        MemoryStore file = new MemoryStore();
        MemoryStore database = new MemoryStore();
        guard(file, database).effectiveDate(JUNE_10);

        file.content = null;
        LocalDate seen = guard(file, database).effectiveDate(JUNE_10.minusMonths(6));

        assertEquals(JUNE_10, seen);
        assertEquals(database.content, file.content);
    }

    @Test
    @DisplayName("copie retouchee a la main : ignoree, la copie la plus recente l'emporte et la remplace")
    void copieRetoucheeIgnoree() {
        MemoryStore file = new MemoryStore();
        MemoryStore database = new MemoryStore();
        guard(file, database).effectiveDate(JUNE_10);

        file.content = LocalDate.of(2020, 1, 1).toEpochDay() + ":00ff00ff";
        LocalDate seen = guard(file, database).effectiveDate(LocalDate.of(2020, 1, 1));

        assertEquals(JUNE_10, seen);
        assertEquals(database.content, file.content);
    }

    @Test
    @DisplayName("horloge figee : la date avance quand meme avec le temps reellement ecoule")
    void horlogeFigeeAvanceAvecLeTemps() {
        MemoryStore file = new MemoryStore();
        ClockGuard guard = guard(file);
        guard.effectiveDate(JUNE_10);

        nanos.addAndGet(TimeUnit.HOURS.toNanos(30));
        assertEquals(JUNE_10.plusDays(1), guard.effectiveDate(JUNE_10));

        nanos.addAndGet(TimeUnit.DAYS.toNanos(3));
        assertEquals(JUNE_10.plusDays(4), guard.effectiveDate(JUNE_10));
        assertEquals(JUNE_10.plusDays(4), guard(file).effectiveDate(JUNE_10),
                "la date gagnee est enregistree : un redemarrage ne la reprend pas");
    }

    @Test
    @DisplayName("horloge juste : la date du jour, sans avance")
    void horlogeJusteSansAvance() {
        ClockGuard guard = guard(new MemoryStore());
        guard.effectiveDate(JUNE_10);

        nanos.addAndGet(TimeUnit.HOURS.toNanos(26));
        assertEquals(JUNE_10.plusDays(1), guard.effectiveDate(JUNE_10.plusDays(1)));
        assertEquals(JUNE_10.plusDays(2), guard.effectiveDate(JUNE_10.plusDays(2)));
    }

    @Test
    @DisplayName("aucune copie possible : la protection tient tant que le serveur tourne")
    void aucuneCopiePossible() {
        MemoryStore refusing = new MemoryStore();
        refusing.refuses = true;
        ClockGuard guard = guard(refusing);

        guard.effectiveDate(JUNE_10);

        assertEquals(JUNE_10, guard.effectiveDate(JUNE_10.minusDays(3)));
    }

    @Test
    @DisplayName("une copie venue d'une autre machine ne recule pas la date ni ne l'avance")
    void copieEtrangereIgnoree() {
        MemoryStore foreign = new MemoryStore();
        new ClockGuard(true, List.of(foreign), ANCHOR, "OTHER-MACHINE", nanos::get)
                .effectiveDate(JUNE_10.plusYears(1));

        LocalDate seen = guard(foreign).effectiveDate(JUNE_10);

        assertEquals(JUNE_10, seen);
        assertNotNull(foreign.content);
    }
}
