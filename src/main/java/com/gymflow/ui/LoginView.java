package com.gymflow.ui;

import java.util.function.Consumer;

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

    static Parent create(Consumer<Screen> navigate) {
        Label mark = new Label("GF");
        mark.getStyleClass().add("brand-mark");
        Label brand = new Label("GYMFLOW");
        brand.getStyleClass().add("login-brand");
        HBox identity = new HBox(12, mark, brand);
        identity.setAlignment(Pos.CENTER);

        Label title = new Label("Welcome back");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Sign in to access your gym account");
        subtitle.getStyleClass().add("muted-text");

        Label emailLabel = new Label("Email address");
        TextField email = new TextField();
        email.setPromptText("name@example.com");
        email.setAccessibleText("Email address");
        emailLabel.setLabelFor(email);

        Label passwordLabel = new Label("Password");
        PasswordField password = new PasswordField();
        password.setPromptText("Enter your password");
        password.setAccessibleText("Password");
        passwordLabel.setLabelFor(password);

        Button signIn = new Button("Sign In");
        signIn.getStyleClass().add("primary-button");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setDisable(true);
        signIn.setAccessibleText("Sign in, authentication will be added in a later commit");

        Label accountNote = new Label("Member accounts are created by the gym owner.");
        accountNote.getStyleClass().add("muted-text");
        accountNote.setWrapText(true);

        Label previewLabel = new Label("DEVELOPMENT PREVIEW");
        previewLabel.getStyleClass().add("preview-label");
        Button ownerPreview = new Button("Preview Owner Dashboard");
        ownerPreview.getStyleClass().add("secondary-button");
        ownerPreview.setOnAction(event -> navigate.accept(Screen.OWNER_HOME));
        Button memberPreview = new Button("Preview Member Dashboard");
        memberPreview.getStyleClass().add("secondary-button");
        memberPreview.setOnAction(event -> navigate.accept(Screen.MEMBER_HOME));
        ownerPreview.setMaxWidth(Double.MAX_VALUE);
        memberPreview.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(10, emailLabel, email, passwordLabel, password, signIn);
        VBox previews = new VBox(8, previewLabel, ownerPreview, memberPreview);
        VBox panel = new VBox(18, identity, title, subtitle, form, accountNote, new Separator(), previews);
        panel.getStyleClass().add("login-panel");
        panel.setMaxWidth(430);

        StackPane root = new StackPane(panel);
        root.setId("login-screen");
        root.getStyleClass().add("login-screen");
        root.setAccessibleText("GymFlow login screen");
        return root;
    }
}

