package com.gymflow.ui;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class MemberHomeView {
    private static final List<String> NAVIGATION =
            List.of("Home", "My Membership", "Gym Visits", "Workouts", "Profile");

    private MemberHomeView() {
    }

    static Parent create(Runnable returnToLogin) {
        Label status = new Label("STATUS PENDING");
        status.getStyleClass().add("status-badge");
        Label membershipTitle = new Label("Membership");
        membershipTitle.getStyleClass().add("section-title");
        Label membershipDates = new Label("Start date  —        Expiry date  —");
        membershipDates.getStyleClass().add("detail-text");
        VBox membership = UiComponents.card(membershipTitle, status, membershipDates);

        Label visitTitle = new Label("Gym visit");
        visitTitle.getStyleClass().add("section-title");
        Label visitStatus = new Label("You are not currently checked in.");
        visitStatus.getStyleClass().add("muted-text");
        Button entry = disabledAction("Submit Entry");
        Button exit = disabledAction("Submit Exit");
        HBox visitActions = new HBox(10, entry, exit);
        VBox visit = UiComponents.card(visitTitle, visitStatus, visitActions);

        GridPane summary = new GridPane();
        summary.setHgap(16);
        summary.add(membership, 0, 0);
        summary.add(visit, 1, 0);
        GridPane.setHgrow(membership, Priority.ALWAYS);
        GridPane.setHgrow(visit, Priority.ALWAYS);
        membership.setMaxWidth(Double.MAX_VALUE);
        visit.setMaxWidth(Double.MAX_VALUE);

        Label historyTitle = new Label("Recent visits");
        historyTitle.getStyleClass().add("section-title");
        ListView<Void> visits = UiComponents.cardList(
                "No gym visits recorded yet", ignored -> new VBox());
        visits.setPrefHeight(260);
        VBox history = UiComponents.card(historyTitle, visits);
        VBox.setVgrow(history, Priority.ALWAYS);

        Label profileTitle = new Label("Profile");
        profileTitle.getStyleClass().add("section-title");
        Label profile = new Label("Member details will appear here after login is implemented.");
        profile.getStyleClass().add("muted-text");
        profile.setWrapText(true);
        VBox profileCard = UiComponents.card(profileTitle, profile);

        VBox mainColumn = new VBox(20, summary, history);
        VBox.setVgrow(history, Priority.ALWAYS);
        HBox body = new HBox(20, mainColumn, profileCard);
        HBox.setHgrow(mainColumn, Priority.ALWAYS);
        profileCard.setPrefWidth(260);

        VBox content = new VBox(20,
                UiComponents.header("Member Home", "Welcome to your GymFlow account", null), body);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        VBox.setVgrow(body, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("member-home-screen");
        root.getStyleClass().add("dashboard-screen");
        root.setLeft(UiComponents.sidebar("Member", NAVIGATION, "Home", returnToLogin));
        root.setCenter(content);
        root.setAccessibleText("GymFlow member dashboard preview");
        return root;
    }

    private static Button disabledAction(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setDisable(true);
        button.setAccessibleText(text + ", coming in a later commit");
        return button;
    }
}
