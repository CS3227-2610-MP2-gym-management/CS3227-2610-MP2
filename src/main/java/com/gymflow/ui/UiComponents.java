package com.gymflow.ui;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.Stage;

final class UiComponents {
    private UiComponents() {
    }

    static VBox sidebar(String role, List<String> items, String activeItem, Runnable returnToLogin) {
        return sidebar(role, items, activeItem, Set.of(activeItem), item -> { }, returnToLogin);
    }

    static VBox sidebar(String role, List<String> items, String activeItem,
            Set<String> enabledItems, Consumer<String> navigate, Runnable returnToLogin) {
        Label brand = new Label("GYMFLOW");
        brand.getStyleClass().add("sidebar-brand");
        Label roleLabel = new Label(role.toUpperCase());
        roleLabel.getStyleClass().add("role-label");

        VBox navigation = new VBox(6);
        navigation.getStyleClass().add("navigation");
        for (String item : items) {
            Button button = new Button(item);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setDisable(!enabledItems.contains(item) || item.equals(activeItem));
            button.getStyleClass().add(item.equals(activeItem) ? "nav-button-active" : "nav-button");
            button.setOnAction(event -> navigate.accept(item));
            button.setAccessibleText(item + (item.equals(activeItem) ? ", current page" : ""));
            navigation.getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button logout = new Button("Return to Login");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.getStyleClass().add("logout-button");
        logout.setOnAction(event -> returnToLogin.run());
        logout.setAccessibleText("Return to the login preview screen");

        VBox sidebar = new VBox(12, brand, roleLabel, navigation, spacer, logout);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(230);
        sidebar.setMinWidth(210);
        return sidebar;
    }

    static VBox card(Node... content) {
        VBox card = new VBox(12, content);
        card.getStyleClass().add("card");
        return card;
    }

    static VBox statCard(String labelText) {
        return statCard(labelText, new Label("—"));
    }

    static VBox statCard(String labelText, Label value) {
        Label label = new Label(labelText);
        label.getStyleClass().add("stat-label");
        value.getStyleClass().add("stat-value");
        VBox card = card(label, value);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    static TableView<Void> emptyTable(String message, String... columnNames) {
        TableView<Void> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(message));
        table.setPrefHeight(260);
        for (String columnName : columnNames) {
            TableColumn<Void, String> column = new TableColumn<>(columnName);
            table.getColumns().add(column);
        }
        return table;
    }

    static HBox header(String titleText, String subtitleText, Node action) {
        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");
        preserveLabelHeight(title);
        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("page-subtitle");
        VBox copy = new VBox(4, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = action == null ? new HBox(copy, spacer) : new HBox(copy, spacer, action);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 8, 0));
        return header;
    }

    static void styleDialog(Dialog<?> dialog, Node owner, String styleClass, boolean tallScrollable) {
        Window ownerWindow = owner.getScene().getWindow();
        dialog.initOwner(ownerWindow);
        dialog.setHeaderText(null);
        if (tallScrollable) {
            ScrollPane content = scrollable(dialog.getDialogPane().getContent());
            content.getStyleClass().add("dialog-scroll");
            content.setPrefViewportHeight(Math.max(400,
                    Math.min(600, ownerWindow.getHeight() - 170)));
            dialog.getDialogPane().setContent(content);
        }
        dialog.getDialogPane().getStylesheets().add(
                GymFlowApp.class.getResource("/styles/app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("gymflow-dialog", styleClass);
        dialog.setOnShown(event -> {
            Stage window = (Stage) dialog.getDialogPane().getScene().getWindow();
            double maximumHeight = ownerWindow.getHeight();
            window.setMaxHeight(maximumHeight);
            if (tallScrollable) {
                double minimumHeight = Math.min(GymFlowApp.MINIMUM_HEIGHT, maximumHeight);
                window.setMinHeight(minimumHeight);
                window.setHeight(Math.min(maximumHeight, Math.max(window.getHeight(), minimumHeight)));
            }
        });
    }

    static ScrollPane scrollable(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("content-scroll");
        return scroll;
    }

    static void preserveLabelHeight(Label label) {
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPadding(new Insets(3, 5, 9, 2));
        label.setWrapText(true);
    }
}
