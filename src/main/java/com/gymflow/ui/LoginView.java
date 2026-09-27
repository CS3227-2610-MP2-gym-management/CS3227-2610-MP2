package com.gymflow.ui;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import com.gymflow.auth.Authenticator;
import com.gymflow.model.Account;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class LoginView {
    private LoginView() {
    }

    static Parent create(Authenticator authentication, Consumer<Account> authenticated) {
        ImageView mark = new ImageView(AppIcon.IMAGE);
        mark.setFitWidth(42);
        mark.setFitHeight(42);
        Label brand = new Label("GYMFLOW");
        brand.getStyleClass().add("login-brand");
        HBox identity = new HBox(12, mark, brand);
        identity.setAlignment(Pos.CENTER);

        Label title = new Label("Welcome back");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Sign in to access your gym account");
        subtitle.getStyleClass().add("muted-text");

        TextField email = field("Email address", "name@example.com");
        PasswordField password = passwordField("Password", "Enter your password");
        VBox form = new VBox(10, labelled("Email address", email), email,
                labelled("Password", password), password);

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        Button submit = new Button("Sign In");
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        form.getChildren().addAll(error, submit);

        submit.setOnAction(event -> {
            char[] supplied = password.getText().toCharArray();
            String suppliedEmail = email.getText();
            run(submit, () -> authentication.authenticate(suppliedEmail, supplied), result -> {
                Optional<Account> account = result;
                if (account.isPresent()) {
                    authenticated.accept(account.get());
                } else {
                    showError(error, "Invalid email or password");
                }
            }, failure -> showError(error, "Unable to connect to GymFlow. Please try again."));
        });

        Label accountNote = new Label("Owner and Member accounts are managed by the gym.");
        accountNote.getStyleClass().add("muted-text");
        accountNote.setWrapText(true);

        VBox panel = new VBox(18, identity, title, subtitle, form, accountNote);
        panel.getStyleClass().add("login-panel");
        panel.setMaxWidth(430);

        StackPane root = new StackPane(panel);
        root.setId("login-screen");
        root.getStyleClass().add("login-screen");
        root.setAccessibleText("GymFlow login screen");
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

    private static void showError(Label error, String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
    }
}
