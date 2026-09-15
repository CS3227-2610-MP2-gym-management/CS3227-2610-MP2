package com.gymflow.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.gymflow.expense.AddExpenseRequest;
import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.Expense;
import com.gymflow.model.ExpenseCategory;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.PaymentOverview;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Owner income and operating-expense ledgers. */
final class OwnerFinancesView {
    private static final String ALL_CATEGORIES = "All Categories";
    private static final List<String> NAVIGATION =
            List.of("Overview", "Members", "Memberships", "Finances", "Visits");
    private static final DateTimeFormatter PAID_AT =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final NumberFormat SGD =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-SG"));

    private OwnerFinancesView() {
    }

    static Parent create(OwnerMemberService members, OwnerExpenseService expenses,
            Account owner, Consumer<Screen> navigate, Runnable logout) {
        Tab incomeTab = new Tab("Income");
        Tab expensesTab = new Tab("Expenses");
        TabPane tabs = new TabPane(incomeTab, expensesTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        incomeTab.setContent(incomeContent(members));
        expensesTab.setContent(expenseContent(expenses, owner, expensesTab));

        VBox card = UiComponents.card(tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Finances", "Review membership income and operating expenses", null), card);
        content.setPadding(new Insets(36));
        VBox.setVgrow(card, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setId("owner-finances-screen");
        root.setLeft(UiComponents.sidebar("Owner", NAVIGATION, "Finances",
                Set.copyOf(NAVIGATION), item -> navigate.accept(switch (item) {
                case "Overview" -> Screen.OWNER_HOME;
                case "Members" -> Screen.OWNER_MEMBERS;
                case "Memberships" -> Screen.OWNER_MEMBERSHIPS;
                case "Visits" -> Screen.OWNER_VISITS;
                default -> Screen.OWNER_FINANCES;
                }), logout));
        root.setCenter(UiComponents.scrollable(content));
        return root;
    }

    private static VBox incomeContent(OwnerMemberService members) {
        TextField search = new TextField();
        search.setPromptText("Search by member name or email");
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("secondary-button");
        HBox searchBar = new HBox(10, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);
        Label error = errorLabel();
        TableView<PaymentOverview> table = paymentTable();
        long[] version = {0};
        Runnable refresh = () -> {
            long request = ++version[0];
            String query = search.getText();
            error.setText("");
            OwnerMembersView.run(null, () -> members.searchPayments(query), result -> {
                if (request == version[0]) {
                    table.getItems().setAll(result);
                }
            }, exception -> {
                if (request == version[0]) {
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
        VBox content = new VBox(12, searchBar, error, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        Platform.runLater(refresh);
        return content;
    }

    private static VBox expenseContent(OwnerExpenseService expenses,
            Account owner, Tab expensesTab) {
        ComboBox<String> category = new ComboBox<>();
        category.getItems().add(ALL_CATEGORIES);
        for (ExpenseCategory value : ExpenseCategory.values()) {
            category.getItems().add(categoryLabel(value));
        }
        category.setValue(ALL_CATEGORIES);
        Button add = new Button("Add Expense");
        add.getStyleClass().add("primary-button");
        Label categoryLabel = new Label("Category");
        UiComponents.preserveLabelHeight(categoryLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, categoryLabel, category, spacer, add);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label error = errorLabel();
        TableView<Expense> table = expenseTable();
        long[] version = {0};
        Runnable refresh = () -> {
            long request = ++version[0];
            String selected = category.getValue();
            error.setText("");
            OwnerMembersView.run(null, () -> ALL_CATEGORIES.equals(selected)
                    ? expenses.listExpenses()
                    : expenses.listExpensesByCategory(categoryValue(selected)), result -> {
                        if (request == version[0]) {
                            table.getItems().setAll(result);
                        }
                    }, exception -> {
                        if (request == version[0]) {
                            error.setText("Unable to access GymFlow data");
                        }
                    });
        };
        category.setOnAction(event -> refresh.run());
        add.setOnAction(event -> {
            expensesTab.getTabPane().getSelectionModel().select(expensesTab);
            showAddExpense(add, expenses, owner, refresh);
        });
        VBox content = new VBox(12, toolbar, error, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        Platform.runLater(refresh);
        return content;
    }

    private static void showAddExpense(Node ownerNode, OwnerExpenseService expenses,
            Account owner, Runnable refresh) {
        DatePicker date = new DatePicker(LocalDate.now());
        UiComponents.calendarOnly(date);
        TextField amount = new TextField();
        amount.setPromptText("Expense amount (SGD)");
        amount.setTextFormatter(UiComponents.decimalAmount());
        ComboBox<PaymentMethod> method = new ComboBox<>();
        method.getItems().setAll(PaymentMethod.values());
        method.setValue(PaymentMethod.CARD);
        ComboBox<ExpenseCategory> category = new ComboBox<>();
        category.getItems().setAll(ExpenseCategory.values());
        category.setValue(ExpenseCategory.MAINTENANCE);
        TextArea description = new TextArea();
        description.setPromptText("Description (optional)");
        description.setWrapText(true);
        description.setPrefRowCount(3);
        Label error = errorLabel();

        GridPane form = new GridPane();
        form.getStyleClass().add("dialog-form");
        addRow(form, 0, "Date", date);
        addRow(form, 1, "Amount (SGD)", amount);
        addRow(form, 2, "Method", method);
        addRow(form, 3, "Category", category);
        addRow(form, 4, "Description", description);
        Label title = new Label("Add Expense");
        title.getStyleClass().add("dialog-title");
        UiComponents.preserveLabelHeight(title);
        Label subtitle = new Label("Record an operating expense. Saved expenses cannot be edited or deleted.");
        subtitle.getStyleClass().add("dialog-subtitle");
        subtitle.setWrapText(true);
        VBox dialogContent = new VBox(12, title, subtitle, error, form);
        dialogContent.getStyleClass().add("dialog-content");

        ButtonType addType = new ButtonType("Add Expense", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Expense");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, addType);
        dialog.getDialogPane().setContent(dialogContent);
        UiComponents.styleDialog(dialog, ownerNode, "membership-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(addType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        submit.getStyleClass().add("primary-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            AddExpenseRequest request;
            try {
                request = new AddExpenseRequest(date.getValue(), new BigDecimal(amount.getText()),
                        method.getValue(), category.getValue(), description.getText());
            } catch (NumberFormatException exception) {
                showError(error, amount, "Enter a valid expense amount");
                return;
            }
            cancel.setDisable(true);
            submit.setText("Adding…");
            OwnerMembersView.run(submit, () -> expenses.addExpense(request, owner.id()), saved -> {
                dialog.close();
                refresh.run();
            }, exception -> {
                cancel.setDisable(false);
                submit.setText("Add Expense");
                showError(error, fieldFor(exception.getMessage(), date, amount, method, category),
                        exception instanceof IllegalArgumentException
                                ? exception.getMessage() : "Unable to access GymFlow data");
            });
        });
        dialog.showAndWait();
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

    private static TableView<Expense> expenseTable() {
        TableView<Expense> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No Expenses found"));
        addColumn(table, "Date", 140, item -> DATE.format(item.expenseDate()));
        addColumn(table, "Amount", 120, item -> SGD.format(item.amount()));
        addColumn(table, "Method", 120, item -> item.method().name());
        addColumn(table, "Category", 160, item -> categoryLabel(item.category()));
        addColumn(table, "Description", 300,
                item -> item.description().isBlank() ? "—" : item.description());
        return table;
    }

    static String formatMembershipPeriod(LocalDate start, LocalDate expiry) {
        return DATE.format(start) + " – " + DATE.format(expiry);
    }

    static BigDecimal net(BigDecimal income, BigDecimal expenses) {
        return income.subtract(expenses);
    }

    private static String categoryLabel(ExpenseCategory category) {
        String name = category.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static ExpenseCategory categoryValue(String label) {
        return ExpenseCategory.valueOf(label.toUpperCase(Locale.ENGLISH).replace(' ', '_'));
    }

    private static Label errorLabel() {
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        return error;
    }

    private static void addRow(GridPane grid, int row, String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-form-label");
        UiComponents.preserveLabelHeight(label);
        grid.addRow(row, label, control);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private static Node fieldFor(String message, Node date, Node amount,
            Node method, Node category) {
        String value = message == null ? "" : message.toLowerCase(Locale.ENGLISH);
        if (value.contains("date")) {
            return date;
        } else if (value.contains("amount")) {
            return amount;
        } else if (value.contains("method")) {
            return method;
        } else if (value.contains("category")) {
            return category;
        }
        return null;
    }

    private static void showError(Label error, Node field, String message) {
        error.setText(message);
        if (field != null) {
            field.requestFocus();
        }
    }

    private static <T> void addColumn(TableView<T> table, String title, double width,
            Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }
}
