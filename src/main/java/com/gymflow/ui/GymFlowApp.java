package com.gymflow.ui;

import javafx.application.Application;
import javafx.stage.Stage;

/** Configures and displays the GymFlow desktop window. */
public final class GymFlowApp extends Application {
    private static final double MINIMUM_WIDTH = 1050;
    private static final double MINIMUM_HEIGHT = 700;

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

        new AppView(stage);
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
