package com.gymflow.ui;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.gymflow.model.VisitOverview;
import com.gymflow.visit.OwnerVisitService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Owner overview of completed and ongoing gym Visits. */
final class OwnerVisitsView {
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Payments", "Visits");

    private OwnerVisitsView() {
    }

    static Parent create(OwnerVisitService visits, Consumer<Screen> navigate, Runnable logout) {
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
        searchButton.setOnAction(event -> refresh.run());
        search.setOnAction(event -> refresh.run());
        search.textProperty().addListener((observable, previous, current) -> {
            if (!previous.isBlank() && current.isBlank()) {
                refresh.run();
            }
        });
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> refresh.run());

        VBox card = UiComponents.card(searchBar, error, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Visits", "Review gym attendance and current visitors", null), card);
        content.setPadding(new Insets(36));
        VBox.setVgrow(card, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-visits-screen");
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Visits",
                Set.of("Overview", "Members", "Memberships", "Visits"),
                item -> navigate.accept(switch (item) {
                case "Overview" -> Screen.OWNER_HOME;
                case "Members" -> Screen.OWNER_MEMBERS;
                case "Memberships" -> Screen.OWNER_MEMBERSHIPS;
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
        return table;
    }

    private static void addColumn(TableView<VisitOverview> table, String title,
            Function<VisitOverview, String> value) {
        TableColumn<VisitOverview, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }
}
