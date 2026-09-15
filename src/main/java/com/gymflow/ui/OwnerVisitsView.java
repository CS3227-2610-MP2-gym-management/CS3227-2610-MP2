package com.gymflow.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.gymflow.model.Account;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;
import com.gymflow.visit.OwnerVisitService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Owner overview of completed and ongoing gym Visits. */
final class OwnerVisitsView {
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Finances", "Visits");
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ISO_LOCAL_TIME;

    private OwnerVisitsView() {
    }

    static Parent create(OwnerVisitService visits, Account owner,
            Consumer<Screen> navigate, Runnable logout) {
        TextField search = new TextField();
        search.setPromptText("Search by member name or email");
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("secondary-button");
        HBox searchBar = new HBox(10, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);

        TableView<VisitOverview> allTable = visitTable("No Visits found");
        TableView<VisitOverview> currentTable = visitTable("No Members are currently visiting");
        Tab allTab = new Tab("All Visits", allTable);
        Tab currentTab = new Tab("Currently Visiting", currentTable);
        TabPane tabs = new TabPane(allTab, currentTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Button correct = new Button("Correct Selected Visit");
        correct.getStyleClass().add("primary-button");
        correct.setDisable(true);

        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        long[] searchVersion = {0};
        Runnable refresh = () -> {
            long request = ++searchVersion[0];
            boolean currentOnly = tabs.getSelectionModel().getSelectedItem() == currentTab;
            TableView<VisitOverview> target = currentOnly ? currentTable : allTable;
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
        Runnable updateAction = () -> correct.setDisable(selectedVisit(
                tabs, currentTab, allTable, currentTable) == null);
        allTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> updateAction.run());
        currentTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> updateAction.run());
        correct.setOnAction(event -> {
            VisitOverview selected = selectedVisit(tabs, currentTab, allTable, currentTable);
            if (selected != null) {
                showCorrectionDialog(correct, visits, selected, owner, refresh);
            }
        });
        searchButton.setOnAction(event -> refresh.run());
        search.setOnAction(event -> refresh.run());
        search.textProperty().addListener((observable, previous, current) -> {
            if (!previous.isBlank() && current.isBlank()) {
                refresh.run();
            }
        });
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    updateAction.run();
                    refresh.run();
                });

        HBox actions = new HBox(correct);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox card = UiComponents.card(searchBar, error, tabs, actions);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Visits", "Review gym attendance and current visitors", null), card);
        content.setPadding(new Insets(36));
        VBox.setVgrow(card, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-visits-screen");
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Visits",
                Set.copyOf(NAVIGATION),
                item -> navigate.accept(switch (item) {
                case "Overview" -> Screen.OWNER_HOME;
                case "Members" -> Screen.OWNER_MEMBERS;
                case "Memberships" -> Screen.OWNER_MEMBERSHIPS;
                case "Finances" -> Screen.OWNER_FINANCES;
                default -> Screen.OWNER_VISITS;
                }), logout));
        root.setCenter(UiComponents.scrollable(content));
        Platform.runLater(refresh);
        return root;
    }

    private static TableView<VisitOverview> visitTable(String emptyMessage) {
        TableView<VisitOverview> table = new TableView<>();
        table.setPlaceholder(new Label(emptyMessage));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Member No.", VisitOverview::memberNumber);
        addColumn(table, "Member", VisitOverview::memberName);
        addColumn(table, "Entry time", item -> VisitFormat.entryTime(item.visit().enteredAt()));
        addColumn(table, "Exit time", item -> VisitFormat.exitTime(item.visit().exitedAt()));
        addColumn(table, "Duration", item -> VisitFormat.duration(
                item.visit().enteredAt(), item.visit().exitedAt()));
        addColumn(table, "Correction", item -> item.visit().correctedAt() == null ? "—" : "Corrected");
        return table;
    }

    private static VisitOverview selectedVisit(TabPane tabs, Tab currentTab,
            TableView<VisitOverview> allTable, TableView<VisitOverview> currentTable) {
        return (tabs.getSelectionModel().getSelectedItem() == currentTab ? currentTable : allTable)
                .getSelectionModel().getSelectedItem();
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

    private static void addColumn(TableView<VisitOverview> table, String title,
            Function<VisitOverview, String> value) {
        TableColumn<VisitOverview, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }
}
