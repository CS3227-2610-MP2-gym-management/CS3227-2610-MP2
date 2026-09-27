package com.gymflow.ui;

import java.nio.file.Path;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.member.MemberAccountService;
import com.gymflow.metric.BodyMetricService;
import com.gymflow.monitoring.AppMonitoring;
import com.gymflow.visit.OwnerVisitService;
import com.gymflow.visit.MemberVisitService;
import com.gymflow.workout.WorkoutService;
import javafx.application.Application;
import javafx.stage.Stage;

/** Configures and displays the GymFlow desktop window. */
public final class GymFlowApp extends Application {
    private static final double MINIMUM_WIDTH = 1050;
    static final double MINIMUM_HEIGHT = 700;
    private AuthenticationService authentication;
    private OwnerAnnouncementService announcements;
    private OwnerExpenseService expenses;
    private boolean ownerExists;
    private OwnerMemberService members;
    private MemberAccountService memberAccounts;
    private OwnerVisitService visits;
    private MemberVisitService memberVisits;
    private WorkoutService workouts;
    private BodyMetricService bodyMetrics;

    /** Initializes local storage before the JavaFX application thread starts. */
    @Override
    public void init() {
        GymFlowDatabase database = new GymFlowDatabase(Path.of("data", "gymflow.db"));
        database.initialize();
        authentication = new AuthenticationService(database);
        announcements = new OwnerAnnouncementService(database);
        expenses = new OwnerExpenseService(database);
        members = new OwnerMemberService(database);
        memberAccounts = new MemberAccountService(database);
        visits = new OwnerVisitService(database);
        memberVisits = new MemberVisitService(database);
        workouts = new WorkoutService(database);
        bodyMetrics = new BodyMetricService(database);
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
        AppIcon.applyTo(stage);
        stage.setMinWidth(MINIMUM_WIDTH);
        stage.setMinHeight(MINIMUM_HEIGHT);

        new AppView(stage, authentication, members, expenses, visits, memberVisits, workouts, bodyMetrics,
                announcements, memberAccounts, ownerExists);
        stage.show();
    }

    /**
     * Launches GymFlow.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        AppMonitoring.start(Path.of("data", "logs"));
        try {
            launch(args);
        } finally {
            AppMonitoring.stop();
        }
    }
}
