package com.houssen.liberoshop.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The shop's day, whatever zone the server computer is set to. */
class BusinessCalendarZoneTest {

    /** 22:30 in London on the 17th is already 01:30 on the 18th in Antananarivo (UTC+3). */
    private final Clock computer = Clock.fixed(Instant.parse("2026-10-17T22:30:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("avec la zone de la boutique, minuit est celui de la boutique, pas celui du PC")
    void zoneDeLaBoutique() {
        BusinessCalendar shop = new BusinessCalendar(computer, "Indian/Antananarivo");

        assertEquals(LocalDate.of(2026, 10, 18), shop.today());
        assertEquals(1, shop.now().getHour());
        assertEquals(ZoneId.of("Indian/Antananarivo"), shop.zone());
    }

    @Test
    @DisplayName("sans zone choisie, celle du PC, comme avant")
    void sansZoneLePc() {
        BusinessCalendar pc = new BusinessCalendar(computer, "  ");

        assertEquals(LocalDate.of(2026, 10, 17), pc.today());
    }
}
