package com.gymflow.ui;

import java.util.Objects;
import java.util.prefs.Preferences;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.model.Account;
import com.gymflow.model.Role;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.visit.OwnerVisitService;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
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
    private final AuthenticationService authentication;
    private final OwnerAnnouncementService announcements;
    private final OwnerExpenseService expenses;
    private final OwnerMemberService members;
    private final OwnerVisitService visits;
    private Account session;
    private boolean ownerExists;
    private Theme theme;

    /**
     * Creates the application view for a stage.
     *
     * @param stage stage that will display GymFlow
     */
    public AppView(Stage stage, AuthenticationService authentication,
            OwnerMemberService members, OwnerExpenseService expenses,
            OwnerVisitService visits, OwnerAnnouncementService announcements, boolean ownerExists) {
        Objects.requireNonNull(stage);
        this.authentication = Objects.requireNonNull(authentication);
        this.announcements = Objects.requireNonNull(announcements);
        this.expenses = Objects.requireNonNull(expenses);
        this.members = Objects.requireNonNull(members);
        this.visits = Objects.requireNonNull(visits);
        this.ownerExists = ownerExists;
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
                        this::show, this::showReset, this::logout);
        case OWNER_MEMBERS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerMembersView.create(members, visits, session, this::show, this::showReset, this::logout);
        case OWNER_MEMBERSHIPS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerMembershipsView.create(members, this::show, this::showReset, this::logout);
        case OWNER_FINANCES -> !isOwnerSession(session)
                ? createLogin()
                : OwnerFinancesView.create(members, expenses, session, this::show, this::showReset, this::logout);
        case OWNER_VISITS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerVisitsView.create(visits, session, this::show, this::showReset, this::logout);
        case OWNER_ANNOUNCEMENTS -> !isOwnerSession(session)
                ? createLogin()
                : OwnerAnnouncementsView.create(announcements, session, this::show, this::showReset, this::logout);
        case MEMBER_HOME -> MemberHomeView.create(() -> show(Screen.LOGIN));
        };
        shell.setCenter(root);
    }

    static boolean isOwnerSession(Account account) {
        return account != null && account.role() == Role.OWNER;
    }

    private Parent createLogin() {
        return LoginView.create(authentication, !ownerExists, this::ownerAuthenticated,
                () -> show(Screen.MEMBER_HOME));
    }

    private void ownerAuthenticated(Account owner) {
        session = owner;
        ownerExists = true;
        show(Screen.OWNER_HOME);
    }

    private void logout() {
        session = null;
        show(Screen.LOGIN);
    }

    private void resetCompleted() {
        session = null;
        ownerExists = false;
        show(Screen.LOGIN);
    }

    private void showReset(Node ownerNode) {
        OwnerResetDialog.show(ownerNode, authentication, session, this::resetCompleted);
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
        HBox bar = new HBox(toggle);
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
