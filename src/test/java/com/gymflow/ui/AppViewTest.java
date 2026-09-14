package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.List;

import com.gymflow.auth.AuthenticationService;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

class AppViewTest {
    @Test
    void navigationApiProvidesOnlyTheThreePreviewScreens() throws NoSuchMethodException {
        assertEquals(List.of(Screen.LOGIN, Screen.OWNER_HOME, Screen.MEMBER_HOME), List.of(Screen.values()));
        assertTrue(Modifier.isFinal(AppView.class.getModifiers()));
        AppView.class.getConstructor(Stage.class, AuthenticationService.class, boolean.class);
        assertEquals(void.class, AppView.class.getMethod("show", Screen.class).getReturnType());
    }
}
