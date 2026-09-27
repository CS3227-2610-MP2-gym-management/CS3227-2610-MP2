package com.gymflow.ui;

import java.util.Objects;

import javafx.scene.image.Image;
import javafx.stage.Stage;

/** Shares the GymFlow brand icon between the login screen and windows. */
final class AppIcon {
    static final Image IMAGE = new Image(Objects.requireNonNull(
            AppIcon.class.getResource("/images/gymflow-icon.png")).toExternalForm());

    private AppIcon() {
    }

    static void applyTo(Stage stage) {
        stage.getIcons().add(IMAGE);
    }
}
