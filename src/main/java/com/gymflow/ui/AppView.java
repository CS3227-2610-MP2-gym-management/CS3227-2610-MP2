package com.gymflow.ui;

import java.util.Objects;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.model.Account;
import com.gymflow.member.OwnerMemberService;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Owns the single application scene and switches its root view. */
public final class AppView {
    private final Scene scene;
    private final AuthenticationService authentication;
    private final OwnerMemberService members;
    private Account session;
    private boolean ownerExists;

    /**
     * Creates the application view for a stage.
     *
     * @param stage stage that will display GymFlow
     */
    public AppView(Stage stage, AuthenticationService authentication,
            OwnerMemberService members, boolean ownerExists) {
        Objects.requireNonNull(stage);
        this.authentication = Objects.requireNonNull(authentication);
        this.members = Objects.requireNonNull(members);
        this.ownerExists = ownerExists;
        scene = new Scene(createLogin(), 1280, 800);
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
        case LOGIN -> createLogin();
        case OWNER_HOME -> session == null
                ? createLogin()
                : OwnerHomeView.create(authentication, members, session,
                        this::show, this::logout, this::resetCompleted);
        case OWNER_MEMBERS -> session == null
                ? createLogin()
                : OwnerMembersView.create(members, session, this::show, this::logout);
        case OWNER_MEMBERSHIPS -> session == null
                ? createLogin()
                : OwnerMembershipsView.create(members, this::show, this::logout);
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
