package com.gymflow.ui;

import java.util.Objects;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.model.Account;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.visit.OwnerVisitService;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Owns the single application scene and switches its root view. */
public final class AppView {
    private final Scene scene;
    private final AuthenticationService authentication;
    private final OwnerAnnouncementService announcements;
    private final OwnerExpenseService expenses;
    private final OwnerMemberService members;
    private final OwnerVisitService visits;
    private Account session;
    private boolean ownerExists;

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
        scene = new Scene(createLogin(), 1280, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                GymFlowApp.class.getResource("/styles/app.css")).toExternalForm());
        stage.setScene(scene);
    }

    /**
     * Displays an application screen.
     *
     * @param screen screen to display
     */
    public void show(Screen screen) {
        Parent root = switch (Objects.requireNonNull(screen)) {
        case LOGIN -> createLogin();
        case OWNER_HOME -> session == null
                ? createLogin()
                : OwnerHomeView.create(authentication, members, expenses, visits, session,
                        this::show, this::logout, this::resetCompleted);
        case OWNER_MEMBERS -> session == null
                ? createLogin()
                : OwnerMembersView.create(members, visits, session, this::show, this::logout);
        case OWNER_MEMBERSHIPS -> session == null
                ? createLogin()
                : OwnerMembershipsView.create(members, this::show, this::logout);
        case OWNER_FINANCES -> session == null
                ? createLogin()
                : OwnerFinancesView.create(members, expenses, session, this::show, this::logout);
        case OWNER_VISITS -> session == null
                ? createLogin()
                : OwnerVisitsView.create(visits, session, this::show, this::logout);
        case OWNER_ANNOUNCEMENTS -> session == null
                ? createLogin()
                : OwnerAnnouncementsView.create(announcements, session, this::show, this::logout);
        case MEMBER_HOME -> MemberHomeView.create(() -> show(Screen.LOGIN));
        };
        scene.setRoot(root);
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
}
