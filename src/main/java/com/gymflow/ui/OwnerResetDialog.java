package com.gymflow.ui;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.model.Account;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Displays the guarded Owner factory-reset workflow. */
final class OwnerResetDialog {
    private OwnerResetDialog() {
    }

    static void show(Node ownerNode, AuthenticationService authentication,
            Account owner, Runnable resetCompleted) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset GymFlow");
        PasswordField password = new PasswordField();
        password.setPromptText("Current Owner password");
        TextField confirmation = new TextField();
        confirmation.setPromptText("Type RESET");
        Label warning = new Label("This permanently removes the Owner and every database-backed gym record. "
                + "This cannot be undone.");
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
        UiComponents.styleDialog(dialog, ownerNode, "reset-dialog", false);
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
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    authentication.resetAll(owner, supplied, confirmation.getText());
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
