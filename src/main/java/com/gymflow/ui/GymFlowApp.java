package com.gymflow.ui;

import java.nio.file.Path;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.member.OwnerMemberService;
import javafx.application.Application;
import javafx.stage.Stage;

/** Configures and displays the GymFlow desktop window. */
public final class GymFlowApp extends Application {
    private static final double MINIMUM_WIDTH = 1050;
    static final double MINIMUM_HEIGHT = 700;
    private AuthenticationService authentication;
    private boolean ownerExists;
    private OwnerMemberService members;

    /** Initializes local storage before the JavaFX application thread starts. */
    @Override
    public void init() {
        GymFlowDatabase database = new GymFlowDatabase(Path.of("data", "gymflow.db"));
        database.initialize();
        authentication = new AuthenticationService(database);
        members = new OwnerMemberService(database);
        ownerExists = authentication.hasOwner();
    }

    /**
     * Starts the application on the login screen.
     *
     * @param stage primary application stage
     */
    @Override
    public void start(Stage stage) {
        stage.setTitle("GymFlow");
        stage.setMinWidth(MINIMUM_WIDTH);
        stage.setMinHeight(MINIMUM_HEIGHT);

        new AppView(stage, authentication, members, ownerExists);
        stage.show();
    }

    /**
     * Launches GymFlow.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
