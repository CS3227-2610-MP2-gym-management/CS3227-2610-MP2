package com.gymflow.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

final class UiComponents {
    private static final List<String> OWNER_NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Finances", "Visits", "Announcements");

    private UiComponents() {
    }

    static VBox sidebar(String role, List<String> items, String activeItem, Runnable returnToLogin) {
        return sidebar(role, items, activeItem, Set.of(activeItem), item -> { }, null, returnToLogin);
    }

    static VBox ownerSidebar(String activeItem, Consumer<Screen> navigate,
            Consumer<Node> resetGymFlow, Runnable returnToLogin) {
        return sidebar("Owner", OWNER_NAVIGATION, activeItem, Set.copyOf(OWNER_NAVIGATION),
                item -> navigate.accept(ownerScreen(item)), resetGymFlow, returnToLogin);
    }

    static Screen ownerScreen(String item) {
        return switch (item) {
        case "Overview" -> Screen.OWNER_HOME;
        case "Members" -> Screen.OWNER_MEMBERS;
        case "Memberships" -> Screen.OWNER_MEMBERSHIPS;
        case "Finances" -> Screen.OWNER_FINANCES;
        case "Visits" -> Screen.OWNER_VISITS;
        case "Announcements" -> Screen.OWNER_ANNOUNCEMENTS;
        default -> throw new IllegalArgumentException("Unknown Owner navigation item: " + item);
        };
    }

    static VBox sidebar(String role, List<String> items, String activeItem,
            Set<String> enabledItems, Consumer<String> navigate,
            Consumer<Node> resetGymFlow, Runnable returnToLogin) {
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

        VBox sidebar = new VBox(12, brand, roleLabel, navigation, spacer);
        if (resetGymFlow != null) {
            Button reset = new Button("Reset GymFlow");
            reset.setMaxWidth(Double.MAX_VALUE);
            reset.getStyleClass().add("danger-button");
            reset.setOnAction(event -> resetGymFlow.accept(reset));
            sidebar.getChildren().add(reset);
        }
        sidebar.getChildren().add(logout);
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

    static <T> ListView<T> cardList(String emptyMessage, Function<T, Node> renderer) {
        ListView<T> list = new ListView<>();
        list.getStyleClass().add("card-list");
        list.setPlaceholder(emptyState(emptyMessage));
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                Node graphic = empty || item == null ? null : renderer.apply(item);
                if (graphic instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                }
                setGraphic(graphic);
            }
        });
        return list;
    }

    static VBox emptyState(String message) {
        Label title = new Label(message);
        title.getStyleClass().add("empty-title");
        VBox empty = new VBox(title);
        empty.getStyleClass().add("empty-state");
        empty.setAlignment(Pos.CENTER);
        return empty;
    }

    static Label cardLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        preserveLabelHeight(label);
        return label;
    }

    static void collapseWhenEmpty(Label label) {
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
    }

    static Label statusLabel() {
        Label label = new Label();
        label.setWrapText(true);
        preserveLabelHeight(label);
        collapseWhenEmpty(label);
        return label;
    }

    static void showStatus(Label label, String message, boolean error) {
        label.getStyleClass().removeAll("dialog-error", "success-text");
        label.getStyleClass().add(error ? "dialog-error" : "success-text");
        label.setText(message);
    }

    static Path chooseCsv(Node ownerNode, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV");
        chooser.setInitialFileName(initialFileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        var selected = chooser.showSaveDialog(ownerNode.getScene().getWindow());
        return selected == null ? null : CsvExporter.csvPath(selected.toPath());
    }

    static void makeActionable(Node card, String accessibleText, Runnable action) {
        card.getStyleClass().add("record-card-actionable");
        card.setFocusTraversable(true);
        card.setAccessibleRole(AccessibleRole.BUTTON);
        card.setAccessibleText(accessibleText);
        card.setOnMouseClicked(event -> action.run());
        card.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                action.run();
            }
        });
    }

    static VBox statCard(String labelText, Label value) {
        Label label = new Label(labelText);
        label.getStyleClass().add("stat-label");
        value.getStyleClass().add("stat-value");
        preserveLabelHeight(label);
        preserveLabelHeight(value);
        VBox card = card(label, value);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMinWidth(200);
        card.setPrefWidth(220);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
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
        if (Theme.isDark(ownerWindow.getScene().getRoot().getStyleClass())) {
            dialog.getDialogPane().getStyleClass().add("dark");
        }
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

    static TextFormatter<String> decimalAmount() {
        return new TextFormatter<>(change -> change.getControlNewText().matches("\\d*(\\.\\d{0,2})?")
                ? change : null);
    }

    static void calendarOnly(DatePicker... pickers) {
        for (DatePicker picker : pickers) {
            picker.setEditable(false);
            picker.getEditor().setFocusTraversable(false);
            picker.getEditor().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> event.consume());
        }
    }
}
