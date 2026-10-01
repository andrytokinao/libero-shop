package com.houssen.liberoshop.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchTextTest {

    @Test
    void foldsAccentsCaseAndPunctuation() {
        assertEquals("cafe malagasy 250g", SearchText.fold("Café  Malagasy 250g"));
        assertEquals("coca cola 1 5 l", SearchText.fold("Coca-Cola 1,5 L"));
    }

    @Test
    void joinsPartsAndSkipsMissingOnes() {
        assertEquals("baguette 12", SearchText.fold("Baguette", null, "", "12"));
    }

    @Test
    void splitsAQueryIntoFoldedWords() {
        assertEquals(List.of("1", "5", "coca"), SearchText.words(" 1.5 COCA "));
        assertEquals(List.of(), SearchText.words("  -- "));
        assertEquals(List.of(), SearchText.words(null));
    }
}
