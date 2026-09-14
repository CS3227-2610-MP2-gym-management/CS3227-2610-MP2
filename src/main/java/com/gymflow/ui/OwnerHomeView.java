package com.gymflow.ui;

import java.util.List;
import java.util.Optional;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.model.Account;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class OwnerHomeView {
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Payments", "Visits");

    private OwnerHomeView() {
    }

    static Parent create(AuthenticationService authentication, Account owner,
            Runnable returnToLogin, Runnable resetCompleted) {
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

        Button reset = new Button("Reset GymFlow");
        reset.getStyleClass().add("danger-button");
        reset.setOnAction(event -> confirmReset(authentication, owner, reset, resetCompleted));
        Label resetNote = new Label("Permanently clear the Owner account and all gym records.");
        resetNote.getStyleClass().add("muted-text");
        Label resetTitle = new Label("Testing reset");
        resetTitle.getStyleClass().add("section-title");
        VBox resetCard = UiComponents.card(resetTitle, resetNote, reset);

        VBox content = new VBox(20,
                UiComponents.header("Owner Overview", "A snapshot of your gym operations", createMember),
                stats,
                tableCard,
                resetCard);
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

    private static void confirmReset(AuthenticationService authentication, Account owner,
            Button resetButton, Runnable resetCompleted) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset GymFlow");
        dialog.setHeaderText("Permanently delete all GymFlow data?");

        PasswordField password = new PasswordField();
        password.setPromptText("Current Owner password");
        password.setAccessibleText("Current Owner password");
        TextField confirmation = new TextField();
        confirmation.setPromptText("Type RESET");
        confirmation.setAccessibleText("Type RESET to confirm");
        Label warning = new Label("This removes the Owner, members, memberships, payments, visits, workouts, "
                + "and all other database-backed gym records. This cannot be undone.");
        warning.setWrapText(true);
        VBox fields = new VBox(10, warning, new Label("Current Owner password"), password,
                new Label("Confirmation text"), confirmation);
        dialog.getDialogPane().setContent(fields);

        ButtonType resetType = new ButtonType("Reset GymFlow", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, resetType);
        dialog.getDialogPane().lookupButton(resetType).getStyleClass().add("danger-button");

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != resetType) {
            return;
        }

        char[] supplied = password.getText().toCharArray();
        String confirmationText = confirmation.getText();
        resetButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                authentication.resetAll(owner, supplied, confirmationText);
                return null;
            }
        };
        task.setOnSucceeded(event -> resetCompleted.run());
        task.setOnFailed(event -> {
            resetButton.setDisable(false);
            String message = task.getException() instanceof IllegalArgumentException
                    ? "The password or RESET confirmation was incorrect. No data was changed."
                    : "GymFlow could not be reset. No data was changed.";
            new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
        });
        Thread.ofVirtual().name("gymflow-reset").start(task);
    }
}
