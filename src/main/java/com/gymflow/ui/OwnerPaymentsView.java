package com.gymflow.ui;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.PaymentOverview;
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

/** Read-only Owner ledger of recorded membership Payments. */
final class OwnerPaymentsView {
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Payments", "Visits");
    private static final DateTimeFormatter PAID_AT =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter MEMBERSHIP_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final NumberFormat SGD =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-SG"));

    private OwnerPaymentsView() {
    }

    static Parent create(OwnerMemberService members,
            Consumer<Screen> navigate, Runnable logout) {
        TextField search = new TextField();
        search.setPromptText("Search by member name or email");
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("secondary-button");
        HBox searchBar = new HBox(10, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);

        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        TableView<PaymentOverview> table = paymentTable();
        long[] searchVersion = {0};
        Runnable refresh = () -> {
            long request = ++searchVersion[0];
            String query = search.getText();
            error.setText("");
            OwnerMembersView.run(null, () -> members.searchPayments(query), result -> {
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
                UiComponents.header("Payments", "Review recorded membership Payments", null), card);
        content.setPadding(new Insets(36));
        VBox.setVgrow(card, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-payments-screen");
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Payments",
                Set.of("Overview", "Members", "Memberships", "Payments", "Visits"),
                item -> navigate.accept(switch (item) {
                case "Overview" -> Screen.OWNER_HOME;
                case "Members" -> Screen.OWNER_MEMBERS;
                case "Memberships" -> Screen.OWNER_MEMBERSHIPS;
                case "Visits" -> Screen.OWNER_VISITS;
                default -> Screen.OWNER_PAYMENTS;
                }), logout));
        root.setCenter(UiComponents.scrollable(content));
        Platform.runLater(refresh);
        return root;
    }

    private static TableView<PaymentOverview> paymentTable() {
        TableView<PaymentOverview> table = new TableView<>();
        table.setPlaceholder(new Label("No Payments found"));
        addColumn(table, "Member No.", 110, PaymentOverview::memberNumber);
        addColumn(table, "Member", 160, PaymentOverview::memberName);
        addColumn(table, "Membership", 220, item -> formatMembershipPeriod(
                item.membershipStart(), item.membershipExpiry()));
        addColumn(table, "Amount", 105, item -> SGD.format(item.payment().amount()));
        addColumn(table, "Method", 100, item -> item.payment().method().name());
        addColumn(table, "Paid at", 190, item -> PAID_AT.format(item.payment().paidAt()));
        addColumn(table, "Reference", 180, item -> item.payment().reference().isBlank()
                ? "—" : item.payment().reference());
        return table;
    }

    static String formatMembershipPeriod(LocalDate start, LocalDate expiry) {
        return MEMBERSHIP_DATE.format(start) + " – " + MEMBERSHIP_DATE.format(expiry);
    }

    private static void addColumn(TableView<PaymentOverview> table, String title, double width,
            Function<PaymentOverview, String> value) {
        TableColumn<PaymentOverview, String> column = new TableColumn<>(title);
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }
}
