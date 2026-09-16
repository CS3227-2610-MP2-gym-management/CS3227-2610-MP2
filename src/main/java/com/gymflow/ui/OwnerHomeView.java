package com.gymflow.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Consumer;

import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.MembershipOverview;
import com.gymflow.visit.OwnerVisitService;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
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
        BigDecimal[] incomeValue = {null};
        BigDecimal[] expenseValue = {null};
        Runnable updateNet = () -> {
            if (incomeValue[0] != null && expenseValue[0] != null) {
                netTotal.setText(SGD.format(OwnerFinancesView.net(incomeValue[0], expenseValue[0])));
            }
        };
        OwnerMembersView.run(null, visits::currentVisitorCount,
                count -> currentVisitorCount.setText(Long.toString(count)), ignored -> { });

        Label sectionTitle = new Label("Member overview");
        sectionTitle.getStyleClass().add("section-title");
        UiComponents.preserveLabelHeight(sectionTitle);
        ListView<MembershipOverview> memberOverview = memberOverviewList();
        OwnerMembersView.run(null, members::ownerDashboard, dashboard -> {
            totalMembers.setText(Long.toString(dashboard.totalMembers()));
            activeMemberships.setText(Long.toString(dashboard.activeMemberships()));
            incomeValue[0] = dashboard.totalIncome();
            income.setText(SGD.format(incomeValue[0]));
            memberOverview.getItems().setAll(dashboard.members());
            updateNet.run();
        }, ignored -> { });
        OwnerMembersView.run(null, expenses::totalExpenses, total -> {
            expenseValue[0] = total;
            expenseTotal.setText(SGD.format(total));
            updateNet.run();
        }, ignored -> { });
        VBox tableCard = UiComponents.card(sectionTitle, memberOverview);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox content = new VBox(20,
                UiComponents.header("Owner Overview", "A snapshot of your gym operations", createMember),
                stats,
                tableCard);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        VBox.setVgrow(memberOverview, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-home-screen");
        root.getStyleClass().add("dashboard-screen");
        root.setLeft(UiComponents.ownerSidebar("Overview", navigate, resetGymFlow, returnToLogin));
        root.setCenter(UiComponents.scrollable(content));
        root.setAccessibleText("GymFlow owner dashboard preview");
        return root;
    }

    private static ListView<MembershipOverview> memberOverviewList() {
        ListView<MembershipOverview> list = UiComponents.cardList("No Members found", item -> {
            Label title = UiComponents.cardLabel(
                    item.memberName() + " · " + item.memberNumber(), "record-title");
            Label email = UiComponents.cardLabel(item.memberEmail(), "record-meta");
            Label membership = UiComponents.cardLabel(item.membership() == null ? "No Membership"
                    : OwnerFinancesView.formatMembershipPeriod(item.membership().startDate(),
                            item.membership().expiryDate()), "record-value");
            Label status = UiComponents.cardLabel(item.membership() == null ? "NONE"
                    : item.membership().status(LocalDate.now()).name(), "status-badge");
            VBox card = new VBox(6, title, email, membership, status);
            card.getStyleClass().add("record-card");
            return card;
        });
        list.setPrefHeight(320);
        return list;
    }

}
