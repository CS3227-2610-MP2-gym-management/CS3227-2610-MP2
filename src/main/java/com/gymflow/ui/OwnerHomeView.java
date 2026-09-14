package com.gymflow.ui;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class OwnerHomeView {
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Payments", "Visits");

    private OwnerHomeView() {
    }

    static Parent create(Runnable returnToLogin) {
        Button createMember = new Button("Create Member");
        createMember.getStyleClass().add("primary-button");
        createMember.setDisable(true);
        createMember.setAccessibleText("Create member, coming in a later commit");

        HBox stats = new HBox(16,
                UiComponents.statCard("Total Members"),
                UiComponents.statCard("Active Memberships"),
                UiComponents.statCard("Currently Visiting"),
                UiComponents.statCard("Revenue"));

        Label sectionTitle = new Label("Member overview");
        sectionTitle.getStyleClass().add("section-title");
        TableView<Void> members = UiComponents.emptyTable(
                "No member data yet", "Member", "Contact", "Membership", "Status");
        VBox tableCard = UiComponents.card(sectionTitle, members);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox content = new VBox(20,
                UiComponents.header("Owner Overview", "A snapshot of your gym operations", createMember),
                stats,
                tableCard);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        VBox.setVgrow(members, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-home-screen");
        root.getStyleClass().add("dashboard-screen");
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Overview", returnToLogin));
        root.setCenter(content);
        root.setAccessibleText("GymFlow owner dashboard preview");
        return root;
    }
}

