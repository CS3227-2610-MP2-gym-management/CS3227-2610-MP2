package com.gymflow.ui;

import java.time.LocalDate;
import java.util.function.Consumer;

import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.MembershipOverview;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Owner Membership overview and search screen. */
final class OwnerMembershipsView {
    private OwnerMembershipsView() {
    }

    static Parent create(OwnerMemberService members, Consumer<Screen> navigate,
            Consumer<Node> resetGymFlow, Runnable logout) {
        TextField search = new TextField();
        search.setPromptText("Search by member name or email");
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("secondary-button");
        HBox searchBar = new HBox(10, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);

        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        UiComponents.collapseWhenEmpty(error);
        ListView<MembershipOverview> list = membershipList();
        long[] searchVersion = {0};
        Runnable refresh = () -> {
            long request = ++searchVersion[0];
            String query = search.getText();
            error.setText("");
            OwnerMembersView.run(null, () -> members.searchMemberships(query), result -> {
                if (request == searchVersion[0]) {
                    list.getItems().setAll(result);
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

        VBox records = new VBox(12, searchBar, error, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Memberships", "Review all purchased Membership periods", null), records);
        content.setPadding(new Insets(36));
        VBox.setVgrow(records, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-memberships-screen");
        root.setLeft(UiComponents.ownerSidebar("Memberships", navigate, resetGymFlow, logout));
        root.setCenter(content);
        Platform.runLater(refresh);
        return root;
    }

    private static ListView<MembershipOverview> membershipList() {
        return UiComponents.cardList("No Memberships found", item -> {
            Label title = UiComponents.cardLabel(
                    item.memberName() + " · " + item.memberNumber(), "record-title");
            Label email = UiComponents.cardLabel(item.memberEmail(), "record-meta");
            Label period = UiComponents.cardLabel(OwnerFinancesView.formatMembershipPeriod(
                    item.membership().startDate(), item.membership().expiryDate()), "record-value");
            Label status = UiComponents.cardLabel(
                    item.membership().status(LocalDate.now()).name(), "status-badge");
            VBox card = new VBox(6, title, email, period, status);
            card.getStyleClass().add("record-card");
            return card;
        });
    }
}
