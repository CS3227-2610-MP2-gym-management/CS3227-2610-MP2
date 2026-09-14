package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class UiResourceTest {
    @Test
    void stylesheetIsPackaged() {
        assertNotNull(GymFlowApp.class.getResource("/styles/app.css"));
    }
}
