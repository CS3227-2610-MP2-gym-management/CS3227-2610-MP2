package com.gymflow.ui;

import java.util.Objects;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Owns the single application scene and switches its root view. */
public final class AppView {
    private final Scene scene;

    /**
     * Creates the application view for a stage.
     *
     * @param stage stage that will display GymFlow
     */
    public AppView(Stage stage) {
        Objects.requireNonNull(stage);
        scene = new Scene(LoginView.create(this::show), 1280, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                GymFlowApp.class.getResource("/styles/app.css")).toExternalForm());
        stage.setScene(scene);
    }

    /**
     * Displays one of the three initial screens.
     *
     * @param screen screen to display
     */
    public void show(Screen screen) {
        Parent root = switch (Objects.requireNonNull(screen)) {
        case LOGIN -> LoginView.create(this::show);
        case OWNER_HOME -> OwnerHomeView.create(() -> show(Screen.LOGIN));
        case MEMBER_HOME -> MemberHomeView.create(() -> show(Screen.LOGIN));
        };
        scene.setRoot(root);
    }
}

