package com.gymflow.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.MembershipOverview;
import com.gymflow.visit.OwnerVisitService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class OwnerHomeView {
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Finances", "Visits", "Announcements");
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
        TableView<MembershipOverview> memberOverview = memberOverviewTable();
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
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Overview",
                Set.copyOf(NAVIGATION),
                item -> navigate.accept(switch (item) {
                case "Members" -> Screen.OWNER_MEMBERS;
                case "Memberships" -> Screen.OWNER_MEMBERSHIPS;
                case "Finances" -> Screen.OWNER_FINANCES;
                case "Visits" -> Screen.OWNER_VISITS;
                case "Announcements" -> Screen.OWNER_ANNOUNCEMENTS;
                default -> Screen.OWNER_HOME;
                }), resetGymFlow,
                returnToLogin));
        root.setCenter(UiComponents.scrollable(content));
        root.setAccessibleText("GymFlow owner dashboard preview");
        return root;
    }

    private static TableView<MembershipOverview> memberOverviewTable() {
        TableView<MembershipOverview> table = new TableView<>();
        table.setPlaceholder(new Label("No Members found"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Member", 220,
                item -> item.memberName() + " · " + item.memberNumber());
        addColumn(table, "Contact", 220, MembershipOverview::memberEmail);
        addColumn(table, "Membership", 220, item -> item.membership() == null ? "—"
                : OwnerFinancesView.formatMembershipPeriod(item.membership().startDate(),
                        item.membership().expiryDate()));
        addColumn(table, "Status", 120, item -> item.membership() == null ? "NONE"
                : item.membership().status(LocalDate.now()).name());
        table.setPrefHeight(260);
        return table;
    }

    private static void addColumn(TableView<MembershipOverview> table, String title, double width,
            Function<MembershipOverview, String> value) {
        TableColumn<MembershipOverview, String> column = new TableColumn<>(title);
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }

}
