package com.gymflow.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.gymflow.member.MemberAccountService;
import com.gymflow.model.Account;
import com.gymflow.model.MemberOverview;
import com.gymflow.model.Membership;
import com.gymflow.model.MembershipNotice;
import com.gymflow.model.Workout;
import com.gymflow.visit.MemberVisitService;
import com.gymflow.workout.WorkoutService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Member dashboard with an active Workout workspace. */
final class MemberHomeView {
    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("h:mm a");
    static final List<String> NAVIGATION = List.of("Home", "My Membership", "Gym Visits", "Workouts",
            "Announcements", "Profile");

    private MemberHomeView() {
    }

    static Parent create(MemberAccountService accounts, MemberVisitService visits, WorkoutService workouts,
            Account session, Consumer<Screen> navigate, Runnable logout) {
        Label status = detail("Loading Workout state…");
        Button checkIn = new Button("Check in");
        Button checkOut = new Button("Check out");
        checkIn.setAccessibleText("Check in to start a Workout");
        checkOut.setAccessibleText("Check out and end the current Workout");
        VBox workspace = new VBox(12);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> loadWorkout(visits, workouts, session, status, checkIn, checkOut, workspace,
                refresh[0]);
        checkIn.setOnAction(event -> {
            if (confirm(checkIn, "Start Workout", "Confirming starts and records a Workout now.")) {
                run(checkIn, checkOut, status, false, () -> visits.checkIn(session), refresh[0]);
            }
        });
        checkOut.setOnAction(event -> {
            if (workspace.getUserData() instanceof ActiveWorkout active
                    && confirm(checkOut, "End Workout",
                            "Confirming saves the displayed exercises and ends the Workout now.")) {
                run(checkIn, checkOut, status, true, () -> workouts.checkOut(session, active.workout().id(),
                        MemberWorkoutsView.draftRequest(active.workout(), active.editor())), refresh[0]);
            }
        });
        VBox main = new VBox(20, UiComponents.card(section("Current Workout"), status,
                new HBox(10, checkIn, checkOut), workspace));
        main.setMaxWidth(700);
        VBox content = new VBox(20, UiComponents.header("Member Home", "Welcome to your GymFlow account", null),
                main);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        refresh[0].run();
        return shell(content, Screen.MEMBER_HOME,
                target -> saveBeforeNavigation(workspace, status, workouts, session, target, navigate), logout);
    }

    private static void loadWorkout(MemberVisitService visits, WorkoutService workouts, Account session, Label status,
            Button checkIn, Button checkOut, VBox workspace, Runnable refresh) {
        checkIn.setDisable(true);
        checkOut.setDisable(true);
        Thread.startVirtualThread(() -> {
            try {
                visits.currentState(session);
                Workout open = workouts.history(session).stream().filter(workout -> workout.endedAt() == null)
                        .findFirst().orElse(null);
                Platform.runLater(() -> showWorkout(status, checkIn, checkOut, workspace, open, workouts, session,
                        refresh));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> status.setText("Unable to load Workout state."));
            }
        });
    }

    private static void showWorkout(Label status, Button checkIn, Button checkOut, VBox workspace, Workout open,
            WorkoutService workouts, Account session, Runnable refresh) {
        boolean active = open != null;
        status.setText(active ? "You are currently checked in." : "You are currently checked out.");
        checkIn.setDisable(active);
        checkOut.setDisable(!active);
        workspace.getChildren().clear();
        workspace.setUserData(null);
        if (active) {
            MemberWorkoutsView.WorkoutDraftEditor editor = MemberWorkoutsView.draftEditor(open);
            Label started = detail("Started: "
                    + CLOCK_TIME.format(open.startedAt().atZone(java.time.ZoneId.systemDefault())));
            workspace.getChildren().addAll(started, editor.view());
            workspace.setUserData(new ActiveWorkout(open, editor));
        }
    }

    private static void run(Button first, Button second, Label status, boolean staysCheckedIn, Runnable action,
            Runnable refresh) {
        first.setDisable(true);
        second.setDisable(true);
        Thread.startVirtualThread(() -> {
            try {
                action.run();
                refresh.run();
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    UiComponents.showStatus(status, exception.getMessage(), true);
                    first.setDisable(staysCheckedIn);
                    second.setDisable(false);
                });
            }
        });
    }

    private static void saveBeforeNavigation(VBox workspace, Label status, WorkoutService workouts, Account session,
            Screen target, Consumer<Screen> navigate) {
        if (!(workspace.getUserData() instanceof ActiveWorkout active)) {
            navigate.accept(target);
            return;
        }
        status.setText("Saving Workout…");
        Thread.startVirtualThread(() -> {
            try {
                workouts.update(session, active.workout().id(),
                        MemberWorkoutsView.draftRequest(active.workout(), active.editor()));
                Platform.runLater(() -> navigate.accept(target));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> UiComponents.showStatus(status, exception.getMessage(), true));
            }
        });
    }

    private static boolean confirm(Button owner, String title, String message) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.CANCEL);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.initOwner(owner.getScene().getWindow());
        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.YES;
    }

    static BorderPane shell(VBox content, Screen screen, Consumer<Screen> navigate, Runnable logout) {
        var scroll = UiComponents.scrollable(content);
        BorderPane root = new BorderPane(scroll);
        root.setId("member-" + screen.name().toLowerCase() + "-screen");
        root.getStyleClass().add("dashboard-screen");
        root.setLeft(UiComponents.sidebar("Member", NAVIGATION, navigationItem(screen), Set.copyOf(NAVIGATION),
                item -> navigate.accept(memberScreen(item)), null, logout));
        return root;
    }

    static void load(MemberAccountService accounts, Account session, Consumer<MemberOverview> success,
            Consumer<String> failure) {
        try {
            Platform.runLater(() -> success.accept(accounts.loadOverview(session)));
        } catch (RuntimeException exception) {
            Platform.runLater(() -> failure.accept("Unable to load your account details."));
        }
    }

    static String membershipText(Membership membership, LocalDate today) {
        return "%s%nStart date: %s%nExpiry date: %s".formatted(membership.status(today), membership.startDate(),
                membership.expiryDate());
    }

    static String membershipSummary(MembershipNotice notice, LocalDate today) {
        return switch (notice.state()) {
        case ACTIVE -> membershipText(notice.membership(), today);
        case UPCOMING -> "Upcoming Membership%nStart date: %s%nExpiry date: %s".formatted(
                notice.membership().startDate(), notice.membership().expiryDate());
        case RENEWAL_NEEDED -> renewalGuidance();
        };
    }

    static String renewalGuidance() {
        return "No current or upcoming Membership is recorded. "
                + "Visit the gym in person to purchase or renew your Membership.";
    }

    private static Label section(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("section-title");
        return label;
    }

    static Label detail(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("detail-text");
        UiComponents.preserveLabelHeight(label);
        return label;
    }

    static String title(Screen screen) {
        return switch (screen) {
        case MEMBER_HOME -> "Member Home";
        case MEMBER_MEMBERSHIP -> "My Membership";
        case MEMBER_VISITS -> "Gym Visits";
        case MEMBER_WORKOUTS -> "Workouts";
        case MEMBER_ANNOUNCEMENTS -> "Announcements";
        case MEMBER_PROFILE -> "Profile";
        default -> throw new IllegalArgumentException("Not a Member screen: " + screen);
        };
    }

    static String navigationItem(Screen screen) {
        return title(screen).equals("Member Home") ? "Home" : title(screen);
    }

    private static Screen memberScreen(String item) {
        return switch (item) {
        case "Home" -> Screen.MEMBER_HOME;
        case "My Membership" -> Screen.MEMBER_MEMBERSHIP;
        case "Gym Visits" -> Screen.MEMBER_VISITS;
        case "Workouts" -> Screen.MEMBER_WORKOUTS;
        case "Announcements" -> Screen.MEMBER_ANNOUNCEMENTS;
        case "Profile" -> Screen.MEMBER_PROFILE;
        default -> throw new IllegalArgumentException("Unknown Member navigation item: " + item);
        };
    }

    private record ActiveWorkout(Workout workout, MemberWorkoutsView.WorkoutDraftEditor editor) {
    }
}
