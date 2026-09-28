package com.gymflow.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.visit.OwnerVisitService;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class OwnerHomeView {
    private static final NumberFormat SGD =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-SG"));

    private OwnerHomeView() {
    }

    static Parent create(OwnerMemberService members, OwnerExpenseService expenses,
            OwnerVisitService visits, Account owner, Consumer<Screen> navigate,
            Consumer<Node> resetGymFlow, Runnable returnToLogin) {
        Button createMember = new Button("Create Member");
        createMember.getStyleClass().add("primary-button");
        createMember.setOnAction(event -> OwnerMembersView.showCreateMember(
                createMember, members, owner, () -> navigate.accept(Screen.OWNER_MEMBERS)));

        Label totalMembers = new Label("—");
        Label activeMemberships = new Label("—");
        Label currentVisitorCount = new Label("—");
        Label income = new Label("—");
        Label expenseTotal = new Label("—");
        Label netTotal = new Label("—");
        FlowPane stats = new FlowPane(16, 16,
                UiComponents.statCard("Total Members", totalMembers),
                UiComponents.statCard("Active Memberships", activeMemberships),
                UiComponents.statCard("Currently Visiting", currentVisitorCount),
                UiComponents.statCard("All-Time Income", income),
                UiComponents.statCard("All-Time Expenses", expenseTotal),
                UiComponents.statCard("All-Time Net", netTotal));
        stats.getChildren().forEach(card -> {
            Region region = (Region) card;
            region.prefWidthProperty().bind(
                    Bindings.max(stats.widthProperty().subtract(32).divide(3), 200));
            region.setPrefHeight(220);
        });
        BigDecimal[] incomeValue = {null};
        BigDecimal[] expenseValue = {null};
        Runnable updateNet = () -> {
            if (incomeValue[0] != null && expenseValue[0] != null) {
                netTotal.setText(SGD.format(OwnerFinancesView.net(incomeValue[0], expenseValue[0])));
            }
        };
        OwnerMembersView.run(null, visits::currentVisitorCount,
                count -> currentVisitorCount.setText(Long.toString(count)), ignored -> { });

        OwnerMembersView.run(null, members::ownerDashboard, dashboard -> {
            totalMembers.setText(Long.toString(dashboard.totalMembers()));
            activeMemberships.setText(Long.toString(dashboard.activeMemberships()));
            incomeValue[0] = dashboard.totalIncome();
            income.setText(SGD.format(incomeValue[0]));
            updateNet.run();
        }, ignored -> { });
        OwnerMembersView.run(null, expenses::totalExpenses, total -> {
            expenseValue[0] = total;
            expenseTotal.setText(SGD.format(total));
            updateNet.run();
        }, ignored -> { });
        VBox content = new VBox(20,
                UiComponents.header("Owner Overview", "A snapshot of your gym operations", createMember),
                stats);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));

        BorderPane root = new BorderPane();
        root.setId("owner-home-screen");
        root.getStyleClass().add("dashboard-screen");
        root.setLeft(UiComponents.ownerSidebar("Overview", navigate, resetGymFlow, returnToLogin));
        root.setCenter(UiComponents.scrollable(content));
        root.setAccessibleText("GymFlow owner dashboard preview");
        return root;
    }

}
