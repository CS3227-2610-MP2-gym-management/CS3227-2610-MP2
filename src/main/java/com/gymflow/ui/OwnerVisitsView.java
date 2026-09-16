package com.gymflow.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import com.gymflow.model.Account;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;
import com.gymflow.visit.OwnerVisitService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Owner overview of completed and ongoing gym Visits. */
final class OwnerVisitsView {
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ISO_LOCAL_TIME;

    private OwnerVisitsView() {
    }

    static Parent create(OwnerVisitService visits, Account owner,
            Consumer<Screen> navigate, Consumer<Node> resetGymFlow, Runnable logout) {
        TextField search = new TextField();
        search.setPromptText("Search by member name or email");
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("secondary-button");
        HBox searchBar = new HBox(10, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);

        Runnable[] refresh = new Runnable[1];
        ListView<VisitOverview> allList = visitList("No Visits found", visits, owner,
                () -> refresh[0].run());
        ListView<VisitOverview> currentList = visitList("No Members are currently visiting",
                visits, owner, () -> refresh[0].run());
        Tab allTab = new Tab("All Visits", allList);
        Tab currentTab = new Tab("Currently Visiting", currentList);
        TabPane tabs = new TabPane(allTab, currentTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        long[] searchVersion = {0};
        refresh[0] = () -> {
            long request = ++searchVersion[0];
            boolean currentOnly = tabs.getSelectionModel().getSelectedItem() == currentTab;
            ListView<VisitOverview> target = currentOnly ? currentList : allList;
            String query = search.getText();
            error.setText("");
            OwnerMembersView.run(null, () -> visits.searchVisits(query, currentOnly), result -> {
                if (request == searchVersion[0]) {
                    target.getItems().setAll(result);
                }
            }, exception -> {
                if (request == searchVersion[0]) {
                    error.setText("Unable to access GymFlow data");
                }
            });
        };
        searchButton.setOnAction(event -> refresh[0].run());
        search.setOnAction(event -> refresh[0].run());
        search.textProperty().addListener((observable, previous, current) -> {
            if (!previous.isBlank() && current.isBlank()) {
                refresh[0].run();
            }
        });
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    refresh[0].run();
                });

        VBox card = UiComponents.card(searchBar, error, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Visits", "Review gym attendance and current visitors", null), card);
        content.setPadding(new Insets(36));
        VBox.setVgrow(card, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-visits-screen");
        root.setLeft(UiComponents.ownerSidebar("Visits", navigate, resetGymFlow, logout));
        root.setCenter(UiComponents.scrollable(content));
        Platform.runLater(refresh[0]);
        return root;
    }

    private static ListView<VisitOverview> visitList(String emptyMessage,
            OwnerVisitService visits, Account owner, Runnable refresh) {
        return UiComponents.cardList(emptyMessage, item -> {
            Label title = UiComponents.cardLabel(
                    item.memberName() + " · " + item.memberNumber(), "record-title");
            Button correct = new Button("Correct");
            correct.getStyleClass().add("secondary-button");
            correct.setOnAction(event -> showCorrectionDialog(correct, visits, item, owner, refresh));
            BorderPane header = new BorderPane(title, null, correct, null, null);
            Visit visit = item.visit();
            VBox card = new VBox(6, header,
                    UiComponents.cardLabel("Entry: " + VisitFormat.entryTime(visit.enteredAt()),
                            "record-meta"),
                    UiComponents.cardLabel("Exit: " + VisitFormat.exitTime(visit.exitedAt())
                            + " · " + VisitFormat.duration(visit.enteredAt(), visit.exitedAt()),
                            "record-value"));
            if (visit.correctedAt() != null) {
                card.getChildren().add(UiComponents.cardLabel("Corrected", "record-meta"));
            }
            card.getStyleClass().add("record-card");
            return card;
        });
    }

    private static void showCorrectionDialog(Node ownerNode, OwnerVisitService visits,
            VisitOverview overview, Account owner, Runnable refresh) {
        Visit visit = overview.visit();
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime entryValue = LocalDateTime.ofInstant(visit.enteredAt(), zone);
        LocalDateTime exitValue = visit.exitedAt() == null
                ? LocalDateTime.now() : LocalDateTime.ofInstant(visit.exitedAt(), zone);
        DatePicker entryDate = calendar(entryValue.toLocalDate());
        TextField entryTime = timeField(entryValue.toLocalTime());
        CheckBox inside = new CheckBox("Member is still inside");
        inside.setSelected(visit.exitedAt() == null);
        DatePicker exitDate = calendar(exitValue.toLocalDate());
        TextField exitTime = timeField(exitValue.toLocalTime());
        inside.selectedProperty().addListener((observable, previous, selected) -> {
            exitDate.setDisable(selected);
            exitTime.setDisable(selected);
        });
        exitDate.setDisable(inside.isSelected());
        exitTime.setDisable(inside.isSelected());
        TextArea reason = new TextArea();
        reason.setPromptText("Explain why this Visit is being corrected");
        reason.setWrapText(true);
        reason.setPrefRowCount(3);
        Label error = errorLabel();
        GridPane form = new GridPane();
        form.getStyleClass().add("dialog-form");
        addRow(form, 0, "Entry date", entryDate);
        addRow(form, 1, "Entry time", entryTime);
        addRow(form, 2, "Visit state", inside);
        addRow(form, 3, "Exit date", exitDate);
        addRow(form, 4, "Exit time", exitTime);
        addRow(form, 5, "Reason", reason);

        VBox content = new VBox(12, dialogTitle("Correct Visit for " + overview.memberName()),
                previousCorrection(visit), error, form);
        content.getStyleClass().add("dialog-content");
        ButtonType saveType = new ButtonType("Save Correction", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Correct Visit");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType);
        dialog.getDialogPane().setContent(content);
        UiComponents.styleDialog(dialog, ownerNode, "member-dialog", true);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        save.getStyleClass().add("primary-button");
        save.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            Instant enteredAt;
            Instant exitedAt;
            try {
                enteredAt = instant(entryDate, entryTime, zone);
                exitedAt = inside.isSelected() ? null : instant(exitDate, exitTime, zone);
            } catch (RuntimeException exception) {
                showError(error, entryTime, "Enter a valid date and time");
                return;
            }
            cancel.setDisable(true);
            save.setText("Saving…");
            String correctionReason = reason.getText();
            OwnerMembersView.run(save,
                    () -> visits.correctVisit(visit.id(), enteredAt, exitedAt,
                            correctionReason, owner.id()),
                    corrected -> {
                        dialog.close();
                        refresh.run();
                    }, exception -> {
                        cancel.setDisable(false);
                        save.setText("Save Correction");
                        showError(error, reason, exception instanceof IllegalArgumentException
                                ? exception.getMessage() : "Unable to access GymFlow data");
                    });
        });
        dialog.showAndWait();
    }

    private static DatePicker calendar(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.setEditable(false);
        picker.getEditor().setFocusTraversable(false);
        return picker;
    }

    private static TextField timeField(LocalTime value) {
        TextField field = new TextField(LOCAL_TIME.format(value));
        field.setPromptText("HH:mm[:ss[.SSS]]");
        return field;
    }

    private static Instant instant(DatePicker date, TextField time, ZoneId zone) {
        if (date.getValue() == null) {
            throw new IllegalArgumentException("Date is required");
        }
        return LocalDateTime.of(date.getValue(), LocalTime.parse(time.getText(), LOCAL_TIME))
                .atZone(zone).toInstant();
    }

    private static Label previousCorrection(Visit visit) {
        Label label = new Label(visit.correctedAt() == null ? "No previous correction"
                : "Previous correction: " + VisitFormat.entryTime(visit.correctedAt())
                        + " by Owner account " + visit.correctedByUserId()
                        + " — " + visit.correctionReason());
        label.getStyleClass().add("dialog-subtitle");
        label.setWrapText(true);
        return label;
    }

    private static Label dialogTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-title");
        UiComponents.preserveLabelHeight(label);
        label.setWrapText(true);
        return label;
    }

    private static Label errorLabel() {
        Label label = new Label();
        label.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(label);
        label.setWrapText(true);
        return label;
    }

    private static void addRow(GridPane form, int row, String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-form-label");
        UiComponents.preserveLabelHeight(label);
        form.addRow(row, label, control);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private static void showError(Label error, Node field, String message) {
        error.setText(message);
        field.requestFocus();
    }

}
