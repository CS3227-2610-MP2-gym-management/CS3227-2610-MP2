package com.gymflow.ui;

import java.nio.file.Path;

import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.auth.Authenticator;
import com.gymflow.auth.SupabaseAuthenticationService;
import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.MemberAccountService;
import com.gymflow.member.OwnerAccountService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.metric.BodyMetricService;
import com.gymflow.monitoring.AppMonitoring;
import com.gymflow.visit.MemberVisitService;
import com.gymflow.visit.OwnerVisitService;
import com.gymflow.workout.WorkoutService;
import javafx.application.Application;
import javafx.stage.Stage;

/** Configures and displays the GymFlow desktop window. */
public final class GymFlowApp extends Application {
    private static final double MINIMUM_WIDTH = 1050;
    static final double MINIMUM_HEIGHT = 700;
    private Authenticator authentication;
    private OwnerAnnouncementService announcements;
    private OwnerExpenseService expenses;
    private boolean localEnvironment;
    private OwnerMemberService members;
    private MemberAccountService memberAccounts;
    private OwnerAccountService ownerAccounts;
    private OwnerVisitService visits;
    private MemberVisitService memberVisits;
    private WorkoutService workouts;
    private BodyMetricService bodyMetrics;

    /** Initializes local storage before the JavaFX application thread starts. */
    @Override
    public void init() {
        SupabaseConfiguration configuration = SupabaseConfiguration.load();
        SupabaseAuthenticationService supabaseAuthentication =
                new SupabaseAuthenticationService(configuration);
        authentication = supabaseAuthentication;
        SupabaseDataClient data = new SupabaseDataClient(configuration, supabaseAuthentication);
        localEnvironment = configuration.environment() == RuntimeEnvironment.LOCAL;
        announcements = new OwnerAnnouncementService(data);
        expenses = new OwnerExpenseService(data);
        members = new OwnerMemberService(data);
        memberAccounts = new MemberAccountService(data, supabaseAuthentication);
        ownerAccounts = new OwnerAccountService(data);
        visits = new OwnerVisitService(data);
        memberVisits = new MemberVisitService(data);
        workouts = new WorkoutService(data);
        bodyMetrics = new BodyMetricService(data);
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

        new AppView(stage, authentication, members, expenses, visits, memberVisits,
                workouts, bodyMetrics, announcements, memberAccounts, ownerAccounts,
                localEnvironment);
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
