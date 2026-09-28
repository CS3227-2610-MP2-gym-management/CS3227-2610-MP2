package com.gymflow.ui;

import java.util.Arrays;
import java.util.function.Consumer;

import com.gymflow.member.OwnerAccountService;
import com.gymflow.model.Account;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Owner-only interface for provisioning and activating additional Owners. */
final class OwnerAccountsView {
    private OwnerAccountsView() {
    }

    static Parent create(OwnerAccountService accounts, Account session,
            Consumer<Screen> navigate, Runnable logout) {
        BorderPane root = new BorderPane();
        root.setId("owner-accounts-screen");
        root.setLeft(UiComponents.ownerSidebar("Owners", navigate, null, logout));

        Button add = new Button("Add Owner");
        add.getStyleClass().add("primary-button");
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        Runnable[] refresh = new Runnable[1];
        ListView<Account> list = ownerList(accounts, session, error, () -> refresh[0].run());
        refresh[0] = () -> OwnerMembersView.run(null, accounts::listOwners,
                result -> {
                    error.setText("");
                    list.getItems().setAll(result);
                }, failure -> error.setText("Unable to access Owner accounts"));
        add.setOnAction(event -> showCreate(add, accounts, refresh[0]));

        VBox.setVgrow(list, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Owners", "Provision and manage gym administrators", add),
                error, list);
        content.setPadding(new Insets(36));
        root.setCenter(content);
        Platform.runLater(refresh[0]);
        return root;
    }

    private static ListView<Account> ownerList(OwnerAccountService accounts, Account session,
            Label error, Runnable refresh) {
        return UiComponents.cardList("No Owner accounts found", owner -> {
            Label email = UiComponents.cardLabel(owner.email(), "record-title");
            Label status = UiComponents.cardLabel(owner.active() ? "ACTIVE" : "INACTIVE",
                    "record-meta");
            Label note = UiComponents.cardLabel(owner.id() == session.id()
                    ? "Current signed-in Owner" : "Gym administrator", "record-value");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button active = new Button(owner.active() ? "Deactivate" : "Activate");
            active.getStyleClass().add(owner.active() ? "danger-button" : "secondary-button");
            active.setDisable(owner.id() == session.id());
            active.setOnAction(event -> showSetActive(active, accounts, owner, error, refresh));
            HBox heading = new HBox(12, email, spacer, active);
            VBox card = new VBox(6, heading, status, note);
            card.getStyleClass().add("record-card");
            return card;
        });
    }

    private static void showCreate(Node ownerNode, OwnerAccountService accounts, Runnable refresh) {
        TextField email = new TextField();
        email.setPromptText("co-owner@example.com");
        PasswordField password = password("Temporary password");
        PasswordField confirm = password("Confirm temporary password");
        PasswordField current = password("Your current password");
        GridPane form = form();
        addRow(form, 0, "Owner email", email);
        addRow(form, 1, "Temporary password", password);
        addRow(form, 2, "Confirm password", confirm);
        addRow(form, 3, "Your current password", current);
        Label error = dialogError();

        ButtonType createType = new ButtonType("Create Owner", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = dialog("Add Owner", createType, form, error);
        UiComponents.styleDialog(dialog, ownerNode, "member-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(createType);
        submit.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            if (!password.getText().equals(confirm.getText())) {
                error.setText("Passwords do not match");
                confirm.requestFocus();
                return;
            }
            char[] newPassword = password.getText().toCharArray();
            char[] currentPassword = current.getText().toCharArray();
            OwnerMembersView.run(submit,
                    () -> accounts.createOwner(email.getText(), newPassword, currentPassword),
                    created -> {
                        clear(password, confirm, current);
                        dialog.close();
                        refresh.run();
                    }, failure -> {
                        clear(password, confirm, current);
                        error.setText(message(failure));
                    });
        });
        dialog.show();
    }

    private static void showSetActive(Node ownerNode, OwnerAccountService accounts, Account owner,
            Label screenError, Runnable refresh) {
        boolean activate = !owner.active();
        PasswordField current = password("Your current password");
        GridPane form = form();
        addRow(form, 0, "Your current password", current);
        Label error = dialogError();
        ButtonType actionType = new ButtonType(activate ? "Activate" : "Deactivate",
                ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = dialog((activate ? "Activate " : "Deactivate ") + owner.email(),
                actionType, form, error);
        UiComponents.styleDialog(dialog, ownerNode, "membership-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(actionType);
        submit.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            char[] currentPassword = current.getText().toCharArray();
            OwnerMembersView.run(submit,
                    () -> accounts.setActive(owner.id(), activate, currentPassword),
                    updated -> {
                        clear(current);
                        dialog.close();
                        screenError.setText("");
                        refresh.run();
                    }, failure -> {
                        clear(current);
                        error.setText(message(failure));
                    });
        });
        dialog.show();
    }

    private static Dialog<ButtonType> dialog(String title, ButtonType action,
            GridPane form, Label error) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(action, ButtonType.CANCEL);
        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        Label note = new Label("Current-password verification is required for Owner changes.");
        note.getStyleClass().add("dialog-subtitle");
        note.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(10, heading, note, error, form));
        return dialog;
    }

    private static GridPane form() {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        return form;
    }

    private static void addRow(GridPane form, int row, String text, Node field) {
        Label label = new Label(text);
        label.setLabelFor(field);
        form.addRow(row, label, field);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private static PasswordField password(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        return field;
    }

    private static Label dialogError() {
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        return error;
    }

    private static void clear(PasswordField... fields) {
        Arrays.stream(fields).forEach(PasswordField::clear);
    }

    private static String message(Throwable failure) {
        String detail = failure.getMessage();
        return detail == null || detail.isBlank() ? "Unable to update Owner account" : detail;
    }
}
