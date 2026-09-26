package com.gymflow.ui;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.gymflow.member.MemberAccountService;
import com.gymflow.model.Account;
import com.gymflow.model.MemberOverview;
import com.gymflow.model.Membership;
import com.gymflow.model.MembershipNotice;
import com.gymflow.model.MembershipNoticeState;
import com.gymflow.model.MemberVisitState;
import com.gymflow.visit.MemberVisitService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Member dashboard showing the authenticated Member's profile and current Membership. */
final class MemberHomeView {
    static final List<String> NAVIGATION = List.of("Home", "My Membership", "Gym Visits", "Workouts",
            "Announcements", "Profile");

    private MemberHomeView() {
    }

    static Parent create(MemberAccountService accounts, MemberVisitService visits, Account session,
            Consumer<Screen> navigate, Runnable logout) {
        Label membership = detail("Loading Membership details…");
        Label profile = detail("Loading profile…");
        Label visitStatus = detail("Loading Visit state…");
        Button checkIn = new Button("Check in");
        Button checkOut = new Button("Check out");
        checkIn.setAccessibleText("Check in to the gym");
        checkOut.setAccessibleText("Check out of the gym");
        checkIn.setDisable(true);
        checkOut.setDisable(true);
        Runnable[] refreshVisit = new Runnable[1];
        refreshVisit[0] = () -> {
            checkIn.setDisable(true);
            checkOut.setDisable(true);
            Thread.startVirtualThread(() -> {
                try {
                    MemberVisitState state = visits.currentState(session);
                    Platform.runLater(() -> setVisitState(visitStatus, checkIn, checkOut, state));
                } catch (RuntimeException exception) {
                    Platform.runLater(() -> visitStatus.setText("Unable to load Visit state."));
                }
            });
        };
        checkIn.setOnAction(event -> changeVisit(visits, session, true, visitStatus,
                checkIn, checkOut, refreshVisit[0]));
        checkOut.setOnAction(event -> changeVisit(visits, session, false, visitStatus,
                checkIn, checkOut, refreshVisit[0]));
        HBox actions = new HBox(10, checkIn, checkOut);
        VBox renewalNotice = renewalNotice();
        VBox main = new VBox(20, UiComponents.card(section("Gym Visit"), visitStatus, actions),
                UiComponents.card(section("Membership"), membership),
                UiComponents.card(section("Profile"), profile));
        main.setMaxWidth(700);
        VBox content = new VBox(20,
                UiComponents.header("Member Home", "Welcome to your GymFlow account", null), renewalNotice, main);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        BorderPane root = shell(content, Screen.MEMBER_HOME, navigate, logout);
        Thread.startVirtualThread(() -> load(accounts, session, overview -> {
            profile.setText(profileText(overview));
            MembershipNotice notice = accounts.membershipNotice(overview);
            membership.setText(membershipSummary(notice, accounts.today()));
            showRenewalNotice(renewalNotice, notice);
        }, message -> {
            profile.setText(message);
            membership.setText(message);
        }));
        refreshVisit[0].run();
        return root;
    }

    private static void changeVisit(MemberVisitService visits, Account session, boolean checkingIn,
            Label status, Button checkIn, Button checkOut, Runnable refresh) {
        checkIn.setDisable(true);
        checkOut.setDisable(true);
        status.setText(checkingIn ? "Checking in…" : "Checking out…");
        Thread.startVirtualThread(() -> {
            try {
                if (checkingIn) {
                    visits.checkIn(session);
                } else {
                    visits.checkOut(session);
                }
                refresh.run();
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    status.setText(exception.getMessage());
                    refresh.run();
                });
            }
        });
    }

    private static void setVisitState(Label status, Button checkIn, Button checkOut, MemberVisitState state) {
        boolean checkedIn = state.checkedIn();
        status.setText(checkedIn ? "You are currently checked in." : "You are currently checked out.");
        checkIn.setDisable(checkedIn);
        checkOut.setDisable(!checkedIn);
    }

    static Parent createPlaceholder(Screen screen, Consumer<Screen> navigate, Runnable logout) {
        Label message = detail("This Member feature will be available in a later update.");
        VBox content = new VBox(20, UiComponents.header(title(screen), "Your GymFlow account", null),
                UiComponents.card(message));
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        return shell(content, screen, navigate, logout);
    }

    static BorderPane shell(VBox content, Screen screen, Consumer<Screen> navigate, Runnable logout) {
        var scroll = UiComponents.scrollable(content);
        scroll.setId("member-" + screen.name().toLowerCase() + "-scroll");
        BorderPane root = new BorderPane(scroll);
        root.setId("member-" + screen.name().toLowerCase() + "-screen");
        root.getStyleClass().add("dashboard-screen");
        root.setLeft(UiComponents.sidebar("Member", NAVIGATION, navigationItem(screen), Set.copyOf(NAVIGATION),
                item -> navigate.accept(memberScreen(item)), null, logout));
        root.setAccessibleText("GymFlow member " + title(screen));
        return root;
    }

    static void load(MemberAccountService accounts, Account session, Consumer<MemberOverview> success,
            Consumer<String> failure) {
        try {
            MemberOverview overview = accounts.loadOverview(session);
            Platform.runLater(() -> success.accept(overview));
        } catch (RuntimeException exception) {
            Platform.runLater(() -> failure.accept("Unable to load your account details."));
        }
    }

    static String membershipText(Membership membership, LocalDate today) {
        return "%s%nStart date: %s%nExpiry date: %s".formatted(
                membership.status(today), membership.startDate(), membership.expiryDate());
    }

    /** Summarizes the Membership period that currently grants, or will grant, access. */
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

    private static VBox renewalNotice() {
        Label title = section("⚠ Membership renewal needed");
        Label body = detail("You do not currently have a Membership that grants access. "
                + "Visit the gym in person to purchase or renew your Membership. "
                + "Gym check-in is unavailable until renewal is recorded.");
        VBox notice = UiComponents.card(title, body);
        notice.getStyleClass().add("renewal-notice");
        notice.setMaxWidth(700);
        notice.setAccessibleText("Membership renewal needed. Gym check-in is unavailable until renewal is recorded.");
        notice.setManaged(false);
        notice.setVisible(false);
        return notice;
    }

    private static void showRenewalNotice(VBox notice, MembershipNotice membershipNotice) {
        boolean renewalNeeded = membershipNotice.state() == MembershipNoticeState.RENEWAL_NEEDED;
        notice.setManaged(renewalNeeded);
        notice.setVisible(renewalNeeded);
    }

    private static String profileText(MemberOverview overview) {
        var member = overview.member();
        String birthDate = member.dateOfBirth() == null ? "Not provided" : member.dateOfBirth().toString();
        return "Name: %s%nMember number: %s%nEmail: %s%nPhone: %s%nDate of birth: %s".formatted(
                member.fullName(), member.memberNumber(), member.email(), member.phoneNumber(), birthDate);
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
        return switch (screen) {
        case MEMBER_HOME -> "Home";
        case MEMBER_MEMBERSHIP -> "My Membership";
        case MEMBER_VISITS -> "Gym Visits";
        case MEMBER_WORKOUTS -> "Workouts";
        case MEMBER_ANNOUNCEMENTS -> "Announcements";
        case MEMBER_PROFILE -> "Profile";
        default -> throw new IllegalArgumentException("Not a Member screen: " + screen);
        };
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
}
