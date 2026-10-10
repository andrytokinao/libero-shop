package com.houssen.liberoshop.desktop;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTrayIconTest {

    private static final Color GREEN = new Color(0x2E9E4F);
    private static final Color BLUE = new Color(0x1144CC);

    @Test
    void withoutALogoTheWholeIconTakesTheColourOfTheState() {
        BufferedImage icon = (BufferedImage) ServerTray.paint(null, GREEN, 32);

        // Left of the letter, inside the disc.
        assertThat(new Color(icon.getRGB(5, 16), true)).isEqualTo(GREEN);
        // The corners stay clear: it is a disc, not a square.
        assertThat(new Color(icon.getRGB(0, 0), true).getAlpha()).isZero();
    }

    @Test
    void withALogoOnlyTheCornerDotTakesTheColourOfTheState() {
        BufferedImage icon = (BufferedImage) ServerTray.paint(logo(256, 256), GREEN, 32);

        assertThat(new Color(icon.getRGB(4, 4), true)).isEqualTo(BLUE);
        assertThat(new Color(icon.getRGB(23, 23), true)).isEqualTo(GREEN);
    }

    @Test
    void aWideLogoIsFittedWholeAndCentred() {
        BufferedImage icon = (BufferedImage) ServerTray.paint(logo(400, 100), GREEN, 32);

        // 32 x 8, in the middle: nothing above it, the logo on the centre line.
        assertThat(new Color(icon.getRGB(4, 4), true).getAlpha()).isZero();
        assertThat(new Color(icon.getRGB(4, 16), true)).isEqualTo(BLUE);
    }

    private static BufferedImage logo(int width, int height) {
        BufferedImage logo = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = logo.createGraphics();
        g.setColor(BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return logo;
    }
}
