package com.gymflow.ui;

import java.util.function.Consumer;

import com.gymflow.member.MemberAccountService;
import com.gymflow.model.Account;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Self-service contact and password settings for the authenticated Member. */
final class MemberProfileView {
    private MemberProfileView() {
    }

    static Parent create(MemberAccountService accounts, Account session, Consumer<Screen> navigate,
            Runnable logout) {
        TextField email = new TextField();
        email.setPromptText("Email address");
        TextField phone = new TextField();
        phone.setPromptText("8-digit number");
        phone.setTextFormatter(new javafx.scene.control.TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,8}") ? change : null));
        Label contactStatus = UiComponents.statusLabel();
        Button saveContact = new Button("Save contact details");
        saveContact.getStyleClass().add("primary-button");
        saveContact.setOnAction(event -> updateContact(accounts, session, email, phone, saveContact, contactStatus));
        VBox contact = UiComponents.card(title("Contact details"), new Label("Email"), email,
                new Label("Phone (+65)"), phone, contactStatus, saveContact);

        PasswordField current = passwordField("Current password");
        PasswordField next = passwordField("New password (12–128 characters)");
        PasswordField confirm = passwordField("Confirm new password");
        Label passwordStatus = UiComponents.statusLabel();
        Button savePassword = new Button("Change password");
        savePassword.getStyleClass().add("primary-button");
        savePassword.setOnAction(event -> changePassword(accounts, session, current, next, confirm,
                savePassword, passwordStatus));
        VBox password = UiComponents.card(title("Password"), current, next, confirm, passwordStatus, savePassword);

        VBox content = new VBox(20, UiComponents.header("Profile", "Manage your contact details and password", null),
                contact, password);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        loadContact(accounts, session, email, phone, contactStatus);
        return MemberHomeView.shell(content, Screen.MEMBER_PROFILE, navigate, logout);
    }

    private static void loadContact(MemberAccountService accounts, Account session, TextField email,
            TextField phone, Label status) {
        Thread.startVirtualThread(() -> {
            try {
                var member = accounts.loadOverview(session).member();
                Platform.runLater(() -> setContact(email, phone, member.email(), member.phoneNumber()));
            } catch (RuntimeException exception) {
                Platform.runLater(() -> UiComponents.showStatus(status,
                        "Unable to load your contact details.", true));
            }
        });
    }

    private static void updateContact(MemberAccountService accounts, Account session, TextField email,
            TextField phone, Button save, Label status) {
        save.setDisable(true);
        Thread.startVirtualThread(() -> {
            try {
                var member = accounts.updateContact(session, email.getText(), phone.getText());
                Platform.runLater(() -> {
                    setContact(email, phone, member.email(), member.phoneNumber());
                    UiComponents.showStatus(status, "Contact details saved.", false);
                    save.setDisable(false);
                });
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    UiComponents.showStatus(status, exception.getMessage(), true);
                    save.setDisable(false);
                });
            }
        });
    }

    private static void changePassword(MemberAccountService accounts, Account session, PasswordField current,
            PasswordField next, PasswordField confirm, Button save, Label status) {
        if (!next.getText().equals(confirm.getText())) {
            UiComponents.showStatus(status, "New password confirmation does not match.", true);
            confirm.requestFocus();
            return;
        }
        char[] currentPassword = current.getText().toCharArray();
        char[] nextPassword = next.getText().toCharArray();
        current.clear();
        next.clear();
        confirm.clear();
        save.setDisable(true);
        Thread.startVirtualThread(() -> {
            try {
                accounts.changePassword(session, currentPassword, nextPassword);
                Platform.runLater(() -> {
                    UiComponents.showStatus(status, "Password changed.", false);
                    save.setDisable(false);
                });
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    UiComponents.showStatus(status, exception.getMessage(), true);
                    save.setDisable(false);
                });
            }
        });
    }

    private static void setContact(TextField email, TextField phone, String savedEmail, String savedPhone) {
        email.setText(savedEmail);
        phone.setText(savedPhone.replaceAll("\\D", "").replaceFirst("^65", ""));
    }

    private static PasswordField passwordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("password-field");
        return field;
    }

    private static Label title(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("section-title");
        return label;
    }
}
