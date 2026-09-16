package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UiComponentsTest {
    @Test
    void mapsEveryOwnerNavigationLabelToItsScreen() {
        assertEquals(Screen.OWNER_HOME, UiComponents.ownerScreen("Overview"));
        assertEquals(Screen.OWNER_MEMBERS, UiComponents.ownerScreen("Members"));
        assertEquals(Screen.OWNER_MEMBERSHIPS, UiComponents.ownerScreen("Memberships"));
        assertEquals(Screen.OWNER_FINANCES, UiComponents.ownerScreen("Finances"));
        assertEquals(Screen.OWNER_VISITS, UiComponents.ownerScreen("Visits"));
        assertEquals(Screen.OWNER_ANNOUNCEMENTS, UiComponents.ownerScreen("Announcements"));
    }
}
