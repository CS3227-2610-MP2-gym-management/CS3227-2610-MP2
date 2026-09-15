package com.gymflow.ui;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.MembershipOverview;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Owner Membership overview and search screen. */
final class OwnerMembershipsView {
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Finances", "Visits");

    private OwnerMembershipsView() {
    }

    static Parent create(OwnerMemberService members, Consumer<Screen> navigate, Runnable logout) {
        TextField search = new TextField();
        search.setPromptText("Search by member name or email");
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("secondary-button");
        HBox searchBar = new HBox(10, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);

        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        TableView<MembershipOverview> table = membershipTable();
        long[] searchVersion = {0};
        Runnable refresh = () -> {
            long request = ++searchVersion[0];
            String query = search.getText();
            error.setText("");
            OwnerMembersView.run(null, () -> members.searchMemberships(query), result -> {
                if (request == searchVersion[0]) {
                    table.getItems().setAll(result);
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

        VBox card = UiComponents.card(searchBar, error, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Memberships", "Review all purchased Membership periods", null), card);
        content.setPadding(new Insets(36));
        VBox.setVgrow(card, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-memberships-screen");
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Memberships",
                Set.copyOf(NAVIGATION),
                item -> navigate.accept(switch (item) {
                case "Overview" -> Screen.OWNER_HOME;
                case "Members" -> Screen.OWNER_MEMBERS;
                case "Finances" -> Screen.OWNER_FINANCES;
                case "Visits" -> Screen.OWNER_VISITS;
                default -> Screen.OWNER_MEMBERSHIPS;
                }), logout));
        root.setCenter(UiComponents.scrollable(content));
        Platform.runLater(refresh);
        return root;
    }

    private static TableView<MembershipOverview> membershipTable() {
        TableView<MembershipOverview> table = new TableView<>();
        table.setPlaceholder(new Label("No Memberships found"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Member No.", MembershipOverview::memberNumber);
        addColumn(table, "Member", MembershipOverview::memberName);
        addColumn(table, "Start", item -> item.membership().startDate().toString());
        addColumn(table, "Expiry", item -> item.membership().expiryDate().toString());
        addColumn(table, "Status", item -> item.membership().status(LocalDate.now()).name());
        return table;
    }

    private static void addColumn(TableView<MembershipOverview> table, String title,
            Function<MembershipOverview, String> value) {
        TableColumn<MembershipOverview, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }
}
