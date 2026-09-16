package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ThemeTest {
    @Test
    void togglesBetweenLightAndDark() {
        assertEquals(Theme.DARK, Theme.LIGHT.toggle());
        assertEquals(Theme.LIGHT, Theme.DARK.toggle());
    }

    @Test
    void appliesOnlyTheSelectedThemeClass() {
        List<String> styles = new ArrayList<>(List.of("app-shell", "dark"));

        Theme.LIGHT.applyTo(styles);
        assertFalse(styles.contains("dark"));

        Theme.DARK.applyTo(styles);
        assertTrue(styles.contains("dark"));
        assertTrue(Theme.isDark(styles));
    }
}
