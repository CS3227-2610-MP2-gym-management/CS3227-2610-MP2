package com.gymflow.ui;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import javafx.event.ActionEvent;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
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

    static Parent create(AuthenticationService authentication, OwnerMemberService members, Account owner,
            Consumer<Screen> navigate, Runnable returnToLogin, Runnable resetCompleted) {
        Button createMember = new Button("Create Member");
        createMember.getStyleClass().add("primary-button");
        createMember.setOnAction(event -> OwnerMembersView.showCreateMember(
                createMember, members, owner, () -> navigate.accept(Screen.OWNER_MEMBERS)));

        HBox stats = new HBox(16,
                UiComponents.statCard("Total Members"),
                UiComponents.statCard("Active Memberships"),
                UiComponents.statCard("Currently Visiting"),
                UiComponents.statCard("Revenue"));

        Label sectionTitle = new Label("Member overview");
        sectionTitle.getStyleClass().add("section-title");
        UiComponents.preserveLabelHeight(sectionTitle);
        TableView<Void> memberOverview = UiComponents.emptyTable(
                "No member data yet", "Member", "Contact", "Membership", "Status");
        VBox tableCard = UiComponents.card(sectionTitle, memberOverview);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Button reset = new Button("Reset GymFlow");
        reset.getStyleClass().add("danger-button");
        reset.setOnAction(event -> confirmReset(authentication, owner, reset, resetCompleted));
        Label resetNote = new Label("Permanently clear the Owner account and all gym records.");
        resetNote.getStyleClass().add("muted-text");
        Label resetTitle = new Label("Testing reset");
        resetTitle.getStyleClass().add("section-title");
        UiComponents.preserveLabelHeight(resetTitle);
        VBox resetCard = UiComponents.card(resetTitle, resetNote, reset);

        VBox content = new VBox(20,
                UiComponents.header("Owner Overview", "A snapshot of your gym operations", createMember),
                stats,
                tableCard,
                resetCard);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        VBox.setVgrow(memberOverview, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-home-screen");
        root.getStyleClass().add("dashboard-screen");
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Overview", Set.of("Overview", "Members"),
                item -> navigate.accept(item.equals("Members") ? Screen.OWNER_MEMBERS : Screen.OWNER_HOME),
                returnToLogin));
        root.setCenter(UiComponents.scrollable(content));
        root.setAccessibleText("GymFlow owner dashboard preview");
        return root;
    }

    private static void confirmReset(AuthenticationService authentication, Account owner,
            Button resetButton, Runnable resetCompleted) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset GymFlow");

        PasswordField password = new PasswordField();
        password.setPromptText("Current Owner password");
        password.setAccessibleText("Current Owner password");
        TextField confirmation = new TextField();
        confirmation.setPromptText("Type RESET");
        confirmation.setAccessibleText("Type RESET to confirm");
        Label warning = new Label("This removes the Owner, members, memberships, payments, visits, workouts, "
                + "and all other database-backed gym records. This cannot be undone.");
        warning.setWrapText(true);
        warning.getStyleClass().add("dialog-warning");
        Label title = new Label("Permanently delete all GymFlow data?");
        title.getStyleClass().add("dialog-title");
        UiComponents.preserveLabelHeight(title);
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        error.setWrapText(true);
        VBox fields = new VBox(10, title, warning, error, new Label("Current Owner password"), password,
                new Label("Confirmation text"), confirmation);
        fields.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(fields);

        ButtonType resetType = new ButtonType("Reset GymFlow", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, resetType);
        UiComponents.styleDialog(dialog, resetButton, "reset-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(resetType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        submit.getStyleClass().add("danger-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            if (!"RESET".equals(confirmation.getText())) {
                error.setText("Enter RESET exactly to confirm.");
                confirmation.requestFocus();
                return;
            }
            submit.setDisable(true);
            cancel.setDisable(true);
            submit.setText("Resetting…");
            char[] supplied = password.getText().toCharArray();
            String confirmationText = confirmation.getText();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    authentication.resetAll(owner, supplied, confirmationText);
                    return null;
                }
            };
            task.setOnSucceeded(done -> {
                dialog.close();
                resetCompleted.run();
            });
            task.setOnFailed(failed -> {
                submit.setDisable(false);
                cancel.setDisable(false);
                submit.setText("Reset GymFlow");
                error.setText(task.getException() instanceof IllegalArgumentException
                        ? "The password was incorrect. No data was changed."
                        : "GymFlow could not be reset. No data was changed.");
                password.requestFocus();
            });
            Thread.ofVirtual().name("gymflow-reset").start(task);
        });
        dialog.showAndWait();
    }
}
