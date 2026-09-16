package com.gymflow.ui;

import java.util.List;

enum Theme {
    LIGHT,
    DARK;

    Theme toggle() {
        return this == LIGHT ? DARK : LIGHT;
    }

    void applyTo(List<String> styleClasses) {
        styleClasses.remove("dark");
        if (this == DARK) {
            styleClasses.add("dark");
        }
    }

    static boolean isDark(List<String> styleClasses) {
        return styleClasses.contains("dark");
    }
}
