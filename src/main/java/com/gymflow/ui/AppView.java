package com.gymflow.ui;

import java.util.Objects;
import java.util.prefs.Preferences;

import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.auth.Authenticator;
import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.MemberAccountService;
import com.gymflow.member.OwnerAccountService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.metric.BodyMetricService;
import com.gymflow.model.Account;
import com.gymflow.model.Role;
import com.gymflow.visit.MemberVisitService;
import com.gymflow.visit.OwnerVisitService;
import com.gymflow.workout.WorkoutService;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/** Owns the single application scene, navigation, and visual theme. */
public final class AppView {
    private static final String DARK_MODE = "darkMode";

    private final BorderPane shell = new BorderPane();
    private final Preferences preferences = Preferences.userNodeForPackage(AppView.class);
    private final Authenticator authentication;
    private final OwnerAnnouncementService announcements;
    private final OwnerExpenseService expenses;
    private final OwnerMemberService members;
    private final MemberAccountService memberAccounts;
    private final OwnerAccountService ownerAccounts;
    private final OwnerVisitService visits;
    private final MemberVisitService memberVisits;
    private final WorkoutService workouts;
    private final BodyMetricService bodyMetrics;
    private Account session;
    private final boolean localEnvironment;
    private Theme theme;

    /**
     * Creates the application view for a stage.
     *
     * @param stage stage that will display GymFlow
     */
    public AppView(Stage stage, Authenticator authentication,
            OwnerMemberService members, OwnerExpenseService expenses,
            OwnerVisitService visits, MemberVisitService memberVisits,
            WorkoutService workouts, BodyMetricService bodyMetrics,
            OwnerAnnouncementService announcements, MemberAccountService memberAccounts,
            OwnerAccountService ownerAccounts, boolean localEnvironment) {
        Objects.requireNonNull(stage);
        this.authentication = Objects.requireNonNull(authentication);
        this.announcements = Objects.requireNonNull(announcements);
        this.expenses = Objects.requireNonNull(expenses);
        this.members = Objects.requireNonNull(members);
        this.memberAccounts = Objects.requireNonNull(memberAccounts);
        this.ownerAccounts = Objects.requireNonNull(ownerAccounts);
        this.visits = Objects.requireNonNull(visits);
        this.memberVisits = Objects.requireNonNull(memberVisits);
        this.workouts = Objects.requireNonNull(workouts);
        this.bodyMetrics = Objects.requireNonNull(bodyMetrics);
        this.localEnvironment = localEnvironment;
        theme = preferences.getBoolean(DARK_MODE, false) ? Theme.DARK : Theme.LIGHT;
        shell.getStyleClass().add("app-shell");
        shell.setTop(themeBar());
        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                GymFlowApp.class.getResource("/styles/app.css")).toExternalForm());
        stage.setScene(scene);
        applyTheme();
        show(Screen.LOGIN);
    }

    /**
     * Displays an application screen.
     *
     * @param screen screen to display
     */
    public void show(Screen screen) {
        Parent root = switch (Objects.requireNonNull(screen)) {
        case LOGIN -> createLogin();
        case OWNER_HOME -> !isOwnerSession(session)
                ? createLogin()
                : OwnerHomeView.create(members, expenses, visits, session,
                        this::show, null, this::logout);
        case OWNER_MEMBERS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerMembersView.create(members, visits, session, this::show, null, this::logout);
        case OWNER_MEMBERSHIPS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerMembershipsView.create(members, this::show, null, this::logout);
        case OWNER_FINANCES -> !isOwnerSession(session)
                ? createLogin()
                : OwnerFinancesView.create(members, expenses, session, this::show, null, this::logout);
        case OWNER_VISITS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerVisitsView.create(visits, session, this::show, null, this::logout);
        case OWNER_ANNOUNCEMENTS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerAnnouncementsView.create(announcements, session, this::show, null, this::logout);
        case OWNER_ACCOUNTS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerAccountsView.create(ownerAccounts, session, this::show, this::logout);
        case MEMBER_HOME -> !isMemberSession(session)
                ? createLogin()
                : MemberHomeView.create(memberAccounts, memberVisits, workouts, session, this::show, this::logout);
        case MEMBER_MEMBERSHIP -> !isMemberSession(session)
                ? createLogin()
                : MemberMembershipView.create(memberAccounts, session, this::show, this::logout);
        case MEMBER_WORKOUTS -> !isMemberSession(session)
                ? createLogin()
                : MemberWorkoutsView.create(workouts, memberAccounts, session, this::show, this::logout);
        case MEMBER_BODY_METRICS -> !isMemberSession(session)
                ? createLogin()
                : MemberBodyMetricsView.create(bodyMetrics, memberAccounts, session, this::show, this::logout);
        case MEMBER_ANNOUNCEMENTS -> !isMemberSession(session)
                ? createLogin()
                : MemberAnnouncementsView.create(announcements, this::show, this::logout);
        case MEMBER_PROFILE -> !isMemberSession(session)
                ? createLogin() : MemberProfileView.create(memberAccounts, session, this::show, this::logout);
        };
        shell.setCenter(root);
    }

    static boolean isOwnerSession(Account account) {
        return account != null && account.role() == Role.OWNER && account.active();
    }

    static boolean isMemberSession(Account account) {
        return account != null && account.role() == Role.MEMBER && account.active();
    }

    private Parent createLogin() {
        return LoginView.create(authentication, this::authenticated);
    }

    private void authenticated(Account account) {
        session = account;
        show(account.role() == Role.OWNER ? Screen.OWNER_HOME : Screen.MEMBER_HOME);
    }

    private void logout() {
        authentication.signOut();
        session = null;
        show(Screen.LOGIN);
    }

    private HBox themeBar() {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("theme-toggle");
        toggle.setSelected(theme == Theme.DARK);
        toggle.setTooltip(new Tooltip());
        updateThemeButton(toggle);
        toggle.setOnAction(event -> {
            theme = theme.toggle();
            preferences.putBoolean(DARK_MODE, theme == Theme.DARK);
            applyTheme();
            updateThemeButton(toggle);
        });
        HBox bar = new HBox(12);
        if (localEnvironment) {
            Label environment = new Label("LOCAL DEVELOPMENT");
            environment.getStyleClass().add("environment-badge");
            bar.getChildren().add(environment);
        }
        bar.getChildren().add(toggle);
        bar.getStyleClass().add("theme-bar");
        return bar;
    }

    private void applyTheme() {
        theme.applyTo(shell.getStyleClass());
    }

    private void updateThemeButton(ToggleButton toggle) {
        String target = theme == Theme.DARK ? "Light mode" : "Dark mode";
        toggle.setText((theme == Theme.DARK ? "☀  " : "☾  ") + target);
        toggle.setAccessibleText("Switch to " + target.toLowerCase());
        toggle.getTooltip().setText("Switch to " + target.toLowerCase());
    }
}
