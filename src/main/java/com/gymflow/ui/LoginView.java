package com.gymflow.ui;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.model.Account;
import com.gymflow.model.Role;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class LoginView {
    private LoginView() {
    }

    static Parent create(AuthenticationService authentication, boolean setupMode,
            Consumer<Account> ownerAuthenticated, Runnable previewMember) {
        Label mark = new Label("GF");
        mark.getStyleClass().add("brand-mark");
        Label brand = new Label("GYMFLOW");
        brand.getStyleClass().add("login-brand");
        HBox identity = new HBox(12, mark, brand);
        identity.setAlignment(Pos.CENTER);

        Label title = new Label(setupMode ? "Set up GymFlow" : "Welcome back");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label(setupMode
                ? "Create the Owner account for this installation"
                : "Sign in to access your gym account");
        subtitle.getStyleClass().add("muted-text");

        TextField email = field("Email address", "name@example.com");
        PasswordField password = passwordField("Password",
                setupMode ? "At least 12 characters" : "Enter your password");
        VBox form = new VBox(10, labelled("Email address", email), email,
                labelled("Password", password), password);

        PasswordField confirmation = passwordField("Confirm password", "Enter the password again");
        if (setupMode) {
            form.getChildren().addAll(labelled("Confirm password", confirmation), confirmation);
        }

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        Button submit = new Button(setupMode ? "Create Owner Account" : "Sign In");
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        form.getChildren().addAll(error, submit);

        if (setupMode) {
            submit.setOnAction(event -> {
                char[] supplied = password.getText().toCharArray();
                char[] repeated = confirmation.getText().toCharArray();
                if (!Arrays.equals(supplied, repeated)) {
                    Arrays.fill(supplied, '\0');
                    Arrays.fill(repeated, '\0');
                    showError(error, "Passwords do not match");
                    return;
                }
                Arrays.fill(repeated, '\0');
                String suppliedEmail = email.getText();
                run(submit, () -> authentication.createOwner(suppliedEmail, supplied),
                        ownerAuthenticated, failure -> showError(error, setupMessage(failure)));
            });
        } else {
            submit.setOnAction(event -> {
                char[] supplied = password.getText().toCharArray();
                String suppliedEmail = email.getText();
                run(submit, () -> authentication.authenticate(suppliedEmail, supplied), result -> {
                    Optional<Account> account = result;
                    if (account.isPresent() && account.get().role() == Role.OWNER) {
                        ownerAuthenticated.accept(account.get());
                    } else {
                        showError(error, "Invalid email or password");
                    }
                }, failure -> showError(error, "Unable to access GymFlow data. Please try again."));
            });
        }

        Label accountNote = new Label(setupMode
                ? "This installation supports one Owner account."
                : "Member accounts are created by the gym owner.");
        accountNote.getStyleClass().add("muted-text");
        accountNote.setWrapText(true);

        Label previewLabel = new Label("DEVELOPMENT PREVIEW");
        previewLabel.getStyleClass().add("preview-label");
        Button memberPreview = new Button("Preview Member Dashboard");
        memberPreview.getStyleClass().add("secondary-button");
        memberPreview.setOnAction(event -> previewMember.run());
        memberPreview.setMaxWidth(Double.MAX_VALUE);

        VBox previews = new VBox(8, previewLabel, memberPreview);
        VBox panel = new VBox(18, identity, title, subtitle, form, accountNote, new Separator(), previews);
        panel.getStyleClass().add("login-panel");
        panel.setMaxWidth(430);

        StackPane root = new StackPane(panel);
        root.setId("login-screen");
        root.getStyleClass().add("login-screen");
        root.setAccessibleText(setupMode ? "GymFlow Owner setup screen" : "GymFlow login screen");
        return root;
    }

    private static TextField field(String accessibleText, String prompt) {
        TextField field = new TextField();
        field.setAccessibleText(accessibleText);
        field.setPromptText(prompt);
        return field;
    }

    private static PasswordField passwordField(String accessibleText, String prompt) {
        PasswordField field = new PasswordField();
        field.setAccessibleText(accessibleText);
        field.setPromptText(prompt);
        return field;
    }

    private static Label labelled(String text, TextField field) {
        Label label = new Label(text);
        label.setLabelFor(field);
        return label;
    }

    private static <T> void run(Button button, Callable<T> operation,
            Consumer<T> success, Consumer<Throwable> failure) {
        button.setDisable(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            button.setDisable(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            button.setDisable(false);
            failure.accept(task.getException());
        });
        Thread.ofVirtual().name("gymflow-auth").start(task);
    }

    private static String setupMessage(Throwable failure) {
        return failure instanceof IllegalArgumentException
                ? failure.getMessage()
                : "Unable to access GymFlow data. Please try again.";
    }

    private static void showError(Label error, String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
    }
}
