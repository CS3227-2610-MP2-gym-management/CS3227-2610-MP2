package com.gymflow.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.AddMembershipRequest;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.Membership;
import com.gymflow.model.MembershipStatus;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.Visit;
import com.gymflow.visit.OwnerVisitService;
import javafx.application.Platform;
import javafx.concurrent.Task;
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
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class OwnerMembersView {
    private static final DateTimeFormatter PAYMENT_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault());
    private static final NumberFormat SGD = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-SG"));

    private OwnerMembersView() {
    }

    static Parent create(OwnerMemberService members, OwnerVisitService visits, Account owner,
            Consumer<Screen> navigate, Consumer<Node> resetGymFlow, Runnable logout) {
        BorderPane root = new BorderPane();
        root.setId("owner-members-screen");
        root.setLeft(UiComponents.ownerSidebar("Members", navigate, resetGymFlow, logout));

        showMemberList(root, members, visits, owner);
        return root;
    }

    private static void showMemberList(BorderPane root, OwnerMemberService members,
            OwnerVisitService visits, Account owner) {
        Runnable backToList = () -> showMemberList(root, members, visits, owner);
        root.setCenter(memberList(members, owner,
                member -> showMemberDetails(root, members, visits, owner, member, backToList)));
    }

    private static VBox memberList(OwnerMemberService members, Account owner, Consumer<Member> openMember) {
        TextField search = new TextField();
        search.setPromptText("Search by name or email");
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("secondary-button");
        HBox searchBar = new HBox(10, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);

        ListView<Member> list = memberList(openMember);
        Button create = new Button("Create Member");
        create.getStyleClass().add("primary-button");
        Label error = dialogError();
        UiComponents.collapseWhenEmpty(error);

        long[] searchVersion = {0};
        Runnable refresh = () -> {
            long request = ++searchVersion[0];
            String query = search.getText();
            error.setText("");
            run(null, () -> members.searchMembers(query), result -> {
                if (request == searchVersion[0]) {
                    list.getItems().setAll(result);
                }
            }, exception -> {
                if (request == searchVersion[0]) {
                    error.setText(message(exception));
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
        create.setOnAction(event -> showCreateMember(create, members, owner, refresh));

        VBox records = new VBox(12, searchBar, error, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox content = new VBox(20, UiComponents.header("Members",
                "Create, search, and open Member profiles", create), records);
        content.setPadding(new Insets(36));
        VBox.setVgrow(records, Priority.ALWAYS);
        Platform.runLater(refresh);
        return content;
    }

    private static ListView<Member> memberList(Consumer<Member> openMember) {
        return UiComponents.cardList("No Members found", member -> {
            Label title = UiComponents.cardLabel(
                    member.fullName() + " · " + member.memberNumber(), "record-title");
            Label email = UiComponents.cardLabel(member.email(), "record-value");
            Label phone = UiComponents.cardLabel(member.phoneNumber(), "record-meta");
            VBox card = new VBox(6, title, email, phone);
            card.getStyleClass().add("record-card");
            UiComponents.makeActionable(card, "Open profile for " + member.fullName(),
                    () -> openMember.accept(member));
            return card;
        });
    }

    private static void showMemberDetails(BorderPane root, OwnerMemberService members,
            OwnerVisitService visits, Account owner, Member member, Runnable backToList) {
        root.setCenter(UiComponents.scrollable(
                memberDetails(root, members, visits, owner, member, backToList)));
    }

    private static VBox memberDetails(BorderPane root, OwnerMemberService members,
            OwnerVisitService visits, Account owner, Member member, Runnable backToList) {
        Button back = new Button("Back to Members");
        back.getStyleClass().add("secondary-button");
        back.setOnAction(event -> backToList.run());
        Button edit = new Button("Edit");
        edit.getStyleClass().add("primary-button");
        edit.setOnAction(event -> showMemberEditor(root, members, visits, owner, member, backToList));
        Button resetPassword = new Button("Reset Password");
        resetPassword.getStyleClass().add("secondary-button");
        Label passwordStatus = new Label();
        passwordStatus.getStyleClass().add("success-text");
        UiComponents.preserveLabelHeight(passwordStatus);
        passwordStatus.setWrapText(true);
        passwordStatus.setManaged(false);
        passwordStatus.setVisible(false);
        resetPassword.setOnAction(event -> showResetPassword(
                resetPassword, members, member, owner, passwordStatus));
        HBox headerActions = new HBox(10, resetPassword, edit);
        headerActions.setAlignment(Pos.CENTER_RIGHT);

        StackPane header = detailHeader(back, member.fullName(), member.memberNumber(), headerActions);
        VBox profile = UiComponents.card(sectionTitle("Profile"),
                detailRow("Email", member.email()),
                detailRow("Phone", member.phoneNumber()),
                detailRow("Date of birth", formatDate(member.dateOfBirth())));
        VBox memberships = membershipCard(root, members, visits, owner, member, backToList, true);
        ListView<Visit> visitHistory = visitList();
        Label visitError = dialogError();
        run(null, () -> visits.visitHistory(member.accountId()),
                result -> visitHistory.getItems().setAll(result),
                exception -> visitError.setText(message(exception)));
        VBox visitCard = UiComponents.card(sectionTitle("Visit history"), visitError, visitHistory);
        ListView<MemberPayment> payments = paymentList();
        Label paymentError = dialogError();
        run(null, () -> members.paymentHistory(member.accountId()),
                result -> payments.getItems().setAll(result),
                exception -> paymentError.setText(message(exception)));
        VBox paymentCard = UiComponents.card(sectionTitle("Payment history"), paymentError, payments);
        VBox.setVgrow(payments, Priority.ALWAYS);
        VBox content = new VBox(20, header, passwordStatus, profile, memberships, visitCard, paymentCard);
        content.setPadding(new Insets(36));
        return content;
    }

    private static void showMemberEditor(BorderPane root, OwnerMemberService members,
            OwnerVisitService visits, Account owner, Member member, Runnable backToList) {
        root.setCenter(UiComponents.scrollable(
                memberEditor(root, members, visits, owner, member, backToList)));
    }

    private static VBox memberEditor(BorderPane root, OwnerMemberService members,
            OwnerVisitService visits, Account owner, Member member, Runnable backToList) {
        TextField email = valueField(member.email());
        TextField name = valueField(member.fullName());
        TextField phone = valueField(localPhoneNumber(member.phoneNumber()));
        phone.setTextFormatter(digits(8));
        Label phonePrefix = new Label("+65");
        phonePrefix.getStyleClass().add("phone-prefix");
        HBox phoneInput = new HBox(8, phonePrefix, phone);
        phoneInput.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(phone, Priority.ALWAYS);
        DatePicker birth = new DatePicker(member.dateOfBirth());
        UiComponents.calendarOnly(birth);

        Label error = dialogError();
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-form");
        addRow(grid, 0, "Email", email);
        addRow(grid, 1, "Full name", name);
        addRow(grid, 2, "Phone", phoneInput);
        addRow(grid, 3, "Date of birth", birth);

        ListView<MemberPayment> payments = paymentList();
        Label paymentError = dialogError();
        run(null, () -> members.paymentHistory(member.accountId()),
                result -> payments.getItems().setAll(result),
                exception -> paymentError.setText(message(exception)));
        VBox profile = UiComponents.card(sectionTitle("Profile"), error, grid);
        VBox memberships = membershipCard(root, members, visits, owner, member, backToList, false);
        VBox paymentCard = UiComponents.card(sectionTitle("Payment history"), paymentError, payments);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> showMemberDetails(root, members, visits, owner, member, backToList));
        Button save = new Button("Save Changes");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> {
            error.setText("");
            save.setDisable(true);
            cancel.setDisable(true);
            save.setText("Saving...");
            run(save, () -> members.updateMember(member.accountId(), email.getText(), name.getText(),
                    phone.getText(), birth.getValue()),
                    updated -> showMemberDetails(root, members, visits, owner, updated, backToList),
                    exception -> {
                        cancel.setDisable(false);
                        save.setText("Save Changes");
                        showError(error, fieldFor(exception.getMessage(), email, null, name, phone,
                                birth, null, null, null, null), message(exception));
                    });
        });
        HBox actions = new HBox(10, cancel, save);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(20, editHeader(member), profile, memberships, paymentCard, actions);
        content.setPadding(new Insets(36));
        return content;
    }

    private static StackPane detailHeader(Node left, String titleText, String subtitleText, Node right) {
        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");
        UiComponents.preserveLabelHeight(title);
        title.setAlignment(Pos.CENTER);
        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("page-subtitle");
        VBox copy = new VBox(4, title, subtitle);
        copy.setAlignment(Pos.CENTER);
        StackPane header = new StackPane(copy, left, right);
        StackPane.setAlignment(left, Pos.CENTER_LEFT);
        StackPane.setAlignment(right, Pos.CENTER_RIGHT);
        return header;
    }

    private static HBox editHeader(Member member) {
        Label title = new Label("Edit " + member.fullName());
        title.getStyleClass().add("page-title");
        UiComponents.preserveLabelHeight(title);
        Label subtitle = new Label(member.memberNumber());
        subtitle.getStyleClass().add("page-subtitle");
        VBox copy = new VBox(4, title, subtitle);
        HBox header = new HBox(copy);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        UiComponents.preserveLabelHeight(label);
        return label;
    }

    private static HBox detailRow(String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("stat-label");
        UiComponents.preserveLabelHeight(name);
        Label detail = new Label(value);
        detail.getStyleClass().add("detail-text");
        UiComponents.preserveLabelHeight(detail);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, name, spacer, detail);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static ListView<MemberPayment> paymentList() {
        ListView<MemberPayment> list = UiComponents.cardList("No payments recorded", payment -> {
            Label amount = UiComponents.cardLabel(SGD.format(payment.amount()), "record-title");
            String reference = payment.reference().isBlank() ? "No reference" : payment.reference();
            VBox card = new VBox(6, amount,
                    UiComponents.cardLabel(PAYMENT_TIME.format(payment.paidAt())
                            + " · " + payment.method().name(), "record-meta"),
                    UiComponents.cardLabel(reference, "record-value"));
            card.getStyleClass().add("record-card");
            return card;
        });
        list.setPrefHeight(240);
        return list;
    }

    private static ListView<Visit> visitList() {
        ListView<Visit> list = UiComponents.cardList("No Visits recorded", visit -> {
            VBox card = new VBox(6,
                    UiComponents.cardLabel("Entry: " + VisitFormat.entryTime(visit.enteredAt()),
                            "record-title"),
                    UiComponents.cardLabel("Exit: " + VisitFormat.exitTime(visit.exitedAt()),
                            "record-meta"),
                    UiComponents.cardLabel(VisitFormat.duration(
                            visit.enteredAt(), visit.exitedAt()), "record-value"));
            card.getStyleClass().add("record-card");
            return card;
        });
        list.setPrefHeight(220);
        return list;
    }

    private static VBox membershipCard(BorderPane root, OwnerMemberService members,
            OwnerVisitService visits, Account owner, Member member,
            Runnable backToList, boolean editable) {
        Label error = dialogError();
        Button add = new Button("Add Membership");
        add.getStyleClass().add("primary-button");
        add.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(12, sectionTitle("Membership history"), spacer);
        heading.setAlignment(Pos.CENTER_LEFT);
        if (editable) {
            heading.getChildren().add(add);
        }
        Runnable reload = () -> showMemberDetails(root, members, visits, owner, member, backToList);
        ListView<Membership> memberships = membershipList(
                editable, members, owner, error, reload);
        add.setOnAction(event -> showAddMembership(add, members, member.accountId(), memberships.getItems(),
                owner.id(), () -> showMemberDetails(root, members, visits, owner, member, backToList)));
        run(null, () -> members.membershipHistory(member.accountId()), result -> {
            memberships.getItems().setAll(result);
            add.setDisable(!editable);
        }, exception -> error.setText(message(exception)));
        VBox card = UiComponents.card(heading, error, memberships);
        VBox.setVgrow(memberships, Priority.ALWAYS);
        return card;
    }

    private static ListView<Membership> membershipList(boolean editable,
            OwnerMemberService members, Account owner, Label error, Runnable reload) {
        ListView<Membership> list = UiComponents.cardList("No Memberships recorded", membership -> {
            Label period = UiComponents.cardLabel(membership.startDate() + " – "
                    + membership.expiryDate(), "record-title");
            Button action = new Button();
            configureMembershipAction(action, membership);
            action.setVisible(editable);
            action.setManaged(editable);
            action.setOnAction(event -> changeMembership(action, members, membership,
                    owner.id(), error, reload));
            BorderPane header = new BorderPane(period, null, action, null, null);
            VBox card = new VBox(6, header, UiComponents.cardLabel(
                    membership.status(LocalDate.now()).name(), "record-meta"));
            card.getStyleClass().add("record-card");
            return card;
        });
        list.setPrefHeight(220);
        return list;
    }

    private static void changeMembership(Button button, OwnerMemberService members,
            Membership membership, long ownerId, Label error, Runnable reload) {
        if (membership.active()) {
            confirmDeactivation(button, members, membership, ownerId, reload);
            return;
        }
        error.setText("");
        button.setText("Reactivating…");
        run(button, () -> members.setMembershipActive(membership.id(), true, ownerId),
                ignored -> reload.run(), exception -> {
                    configureMembershipAction(button, membership);
                    error.setText(message(exception));
                });
    }

    private static void configureMembershipAction(Button button, Membership membership) {
        button.getStyleClass().removeAll("primary-button", "danger-button", "secondary-button");
        if (membership.active()) {
            button.setText("Deactivate");
            button.getStyleClass().add("danger-button");
            button.setDisable(false);
        } else if (membership.status(LocalDate.now()) == MembershipStatus.DEACTIVATED
                && membership.expiryDate().isBefore(LocalDate.now())) {
            button.setText("Expired");
            button.getStyleClass().add("secondary-button");
            button.setDisable(true);
        } else {
            button.setText("Reactivate");
            button.getStyleClass().add("primary-button");
            button.setDisable(false);
        }
    }

    private static void showAddMembership(Node ownerNode, OwnerMemberService members,
            long memberId, List<Membership> history, long ownerAccountId, Runnable success) {
        LocalDate startDate = history.stream()
                .filter(Membership::active)
                .map(Membership::expiryDate)
                .max(LocalDate::compareTo)
                .map(date -> date.plusDays(1))
                .orElse(LocalDate.now());
        DatePicker start = new DatePicker(startDate);
        DatePicker expiry = new DatePicker(startDate.plusMonths(1));
        UiComponents.calendarOnly(start, expiry);
        TextField amount = field("Payment amount (SGD)");
        amount.setTextFormatter(UiComponents.decimalAmount());
        ComboBox<PaymentMethod> method = new ComboBox<>();
        method.getItems().setAll(PaymentMethod.values());
        method.setValue(PaymentMethod.CARD);
        TextField reference = field("Payment reference (optional)");
        Label error = dialogError();
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-form");
        addRow(grid, 0, "Membership start", start);
        addRow(grid, 1, "Membership expiry", expiry);
        addRow(grid, 2, "Amount (SGD)", amount);
        addRow(grid, 3, "Method", method);
        addRow(grid, 4, "Reference", reference);

        ButtonType addType = new ButtonType("Add Membership", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = formDialog("Add Membership", addType);
        dialog.getDialogPane().setContent(dialogContent("Add Membership",
                "Create one access period and its Payment.", error, grid));
        UiComponents.styleDialog(dialog, ownerNode, "membership-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(addType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        submit.getStyleClass().add("primary-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            AddMembershipRequest request;
            try {
                request = new AddMembershipRequest(memberId, start.getValue(), expiry.getValue(),
                        new BigDecimal(amount.getText()), method.getValue(), Instant.now(), reference.getText());
            } catch (NumberFormatException exception) {
                showError(error, amount, "Enter a valid payment amount");
                return;
            }
            cancel.setDisable(true);
            submit.setText("Adding…");
            run(submit, () -> members.addMembership(request, ownerAccountId), ignored -> {
                dialog.close();
                success.run();
            }, exception -> {
                cancel.setDisable(false);
                submit.setText("Add Membership");
                showError(error, fieldFor(exception.getMessage(), null, null, null, null,
                        null, start, expiry, amount, method), message(exception));
            });
        });
        dialog.showAndWait();
    }

    private static void confirmDeactivation(Node ownerNode, OwnerMemberService members,
            Membership membership, long ownerAccountId, Runnable success) {
        ButtonType deactivateType = new ButtonType("Deactivate", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = formDialog("Deactivate Membership", deactivateType);
        Label title = new Label("Deactivate this Membership?");
        title.getStyleClass().add("dialog-title");
        UiComponents.preserveLabelHeight(title);
        Label warning = new Label("This period will stop granting gym access. "
                + "The Member account and history will remain available.");
        warning.getStyleClass().add("dialog-warning");
        warning.setWrapText(true);
        Label error = dialogError();
        VBox content = new VBox(12, title, warning, error);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);
        UiComponents.styleDialog(dialog, ownerNode, "membership-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(deactivateType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        submit.getStyleClass().add("danger-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            cancel.setDisable(true);
            submit.setText("Deactivating…");
            run(submit, () -> members.setMembershipActive(membership.id(), false, ownerAccountId),
                    ignored -> {
                        dialog.close();
                        success.run();
                    }, exception -> {
                        cancel.setDisable(false);
                        submit.setText("Deactivate");
                        error.setText(message(exception));
                    });
        });
        dialog.showAndWait();
    }

    private static void showResetPassword(Node ownerNode, OwnerMemberService members,
            Member member, Account owner, Label status) {
        PasswordField password = new PasswordField();
        password.setPromptText("New password (12–128 characters)");
        password.setAccessibleText("New Member password");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");
        confirm.setAccessibleText("Confirm new Member password");
        Label error = dialogError();
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-form");
        addRow(grid, 0, "New password", password);
        addRow(grid, 1, "Confirm password", confirm);

        ButtonType resetType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = formDialog("Reset Member Password", resetType);
        dialog.getDialogPane().setContent(dialogContent("Reset Member Password",
                "Set a new password for " + member.fullName() + ".", error, grid));
        UiComponents.styleDialog(dialog, ownerNode, "membership-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(resetType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        submit.getStyleClass().add("primary-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            if (!password.getText().equals(confirm.getText())) {
                showError(error, confirm, "Passwords do not match");
                return;
            }
            cancel.setDisable(true);
            submit.setText("Resetting…");
            char[] replacement = password.getText().toCharArray();
            run(submit, () -> {
                members.resetMemberPassword(member.accountId(), replacement, owner.id());
                return null;
            }, ignored -> {
                dialog.close();
                status.setText("Password reset successfully.");
                status.setManaged(true);
                status.setVisible(true);
            }, exception -> {
                cancel.setDisable(false);
                submit.setText("Reset Password");
                showError(error, password, message(exception));
            });
        });
        dialog.showAndWait();
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "Not provided" : date.toString();
    }

    static void showCreateMember(Node ownerNode, OwnerMemberService members,
            Account owner, Runnable success) {
        ButtonType createType = new ButtonType("Create Member", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = formDialog("Create Member", createType);
        TextField email = field("Email");
        PasswordField password = new PasswordField();
        password.setPromptText("Initial password (12–128 characters)");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm password");
        TextField name = field("Full name");
        TextField phone = field("8-digit number");
        phone.setTextFormatter(digits(8));
        Label phonePrefix = new Label("+65");
        phonePrefix.getStyleClass().add("phone-prefix");
        HBox phoneInput = new HBox(8, phonePrefix, phone);
        phoneInput.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(phone, Priority.ALWAYS);
        DatePicker birth = new DatePicker();
        DatePicker start = new DatePicker(LocalDate.now());
        DatePicker expiry = new DatePicker(LocalDate.now().plusMonths(1));
        TextField amount = field("Payment amount (SGD)");
        amount.setTextFormatter(UiComponents.decimalAmount());
        ComboBox<PaymentMethod> method = new ComboBox<>();
        method.getItems().setAll(PaymentMethod.values());
        method.setValue(PaymentMethod.CARD);
        TextField reference = field("Payment reference (optional)");
        UiComponents.calendarOnly(birth, start, expiry);
        Label error = dialogError();
        GridPane grid = grid(email, password, confirm, name, phoneInput, birth,
                start, expiry, amount, method, reference);
        VBox content = dialogContent("Create Member", "Set up the account, membership, and initial payment.",
                error, grid);
        dialog.getDialogPane().setContent(content);
        UiComponents.styleDialog(dialog, ownerNode, "member-dialog", true);
        Button submit = (Button) dialog.getDialogPane().lookupButton(createType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        submit.getStyleClass().add("primary-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            if (!password.getText().equals(confirm.getText())) {
                showError(error, confirm, "Passwords do not match");
                return;
            }
            CreateMemberRequest request;
            try {
                request = new CreateMemberRequest(email.getText(), password.getText().toCharArray(),
                        name.getText(), phone.getText(), birth.getValue(), start.getValue(), expiry.getValue(),
                        new BigDecimal(amount.getText()), method.getValue(), Instant.now(), reference.getText());
            } catch (NumberFormatException exception) {
                showError(error, amount, "Enter a valid payment amount");
                return;
            }
            submit.setDisable(true);
            cancel.setDisable(true);
            submit.setText("Creating…");
            run(submit, () -> members.createMember(request, owner.id()), ignored -> {
                dialog.close();
                success.run();
            }, exception -> {
                cancel.setDisable(false);
                submit.setText("Create Member");
                showError(error, fieldFor(exception.getMessage(), email, password, name, phone,
                        birth, start, expiry, amount, method), message(exception));
            });
        });
        dialog.showAndWait();
    }

    private static GridPane grid(Object... controls) {
        String[] labels = {"Email", "Initial password", "Confirm password", "Full name", "Phone",
            "Date of birth", "Membership start", "Membership expiry", "Amount (SGD)", "Method", "Reference"};
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("dialog-form");
        for (int index = 0; index < controls.length; index++) {
            addRow(grid, index, labels[index], (javafx.scene.Node) controls[index]);
        }
        return grid;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("dialog-form-label");
        UiComponents.preserveLabelHeight(fieldLabel);
        grid.addRow(row, fieldLabel, control);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private static Dialog<ButtonType> formDialog(String title, ButtonType submitType) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, submitType);
        return dialog;
    }

    private static TextField field(String promptOrValue) {
        TextField field = new TextField();
        field.setPromptText(promptOrValue);
        return field;
    }

    private static TextField valueField(String value) {
        TextField field = new TextField(value);
        return field;
    }

    private static TextFormatter<String> digits(int maximumLength) {
        return new TextFormatter<>(change -> change.getControlNewText().matches("\\d{0," + maximumLength + "}")
                ? change : null);
    }

    private static String localPhoneNumber(String storedNumber) {
        String digitsOnly = storedNumber.replaceAll("\\D", "");
        return digitsOnly.length() == 10 && digitsOnly.startsWith("65")
                ? digitsOnly.substring(2) : digitsOnly;
    }

    private static VBox dialogContent(String titleText, String subtitleText, Label error, Node form) {
        Label title = new Label(titleText);
        title.getStyleClass().add("dialog-title");
        UiComponents.preserveLabelHeight(title);
        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("dialog-subtitle");
        VBox content = new VBox(8, title, subtitle, error, form);
        content.getStyleClass().add("dialog-content");
        return content;
    }

    private static Label dialogError() {
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        return error;
    }

    private static void showError(Label error, Node field, String message) {
        error.setText(message);
        Platform.runLater(() -> {
            Node parent = error.getParent();
            while (parent != null && !(parent instanceof ScrollPane)) {
                parent = parent.getParent();
            }
            if (parent instanceof ScrollPane scroll) {
                scroll.setVvalue(0);
            }
        });
        if (field != null) {
            field.requestFocus();
        }
    }

    private static Node fieldFor(String message, TextField email, PasswordField password,
            TextField name, TextField phone, DatePicker birth, DatePicker start,
            DatePicker expiry, TextField amount, ComboBox<PaymentMethod> method) {
        if (message == null) {
            return null;
        }
        if (message.contains("email")) {
            return email;
        } else if (message.contains("Password")) {
            return password;
        } else if (message.contains("Full name")) {
            return name;
        } else if (message.contains("phone")) {
            return phone;
        } else if (message.contains("12 years")) {
            return birth;
        } else if (message.contains("Membership")) {
            return expiry == null ? start : expiry;
        } else if (message.contains("amount")) {
            return amount;
        } else if (message.contains("method")) {
            return method;
        }
        return null;
    }

    private static String message(Throwable exception) {
        return exception instanceof IllegalArgumentException
                ? exception.getMessage() : "Unable to access GymFlow data";
    }

    static <T> void run(Button button, java.util.concurrent.Callable<T> operation,
            Consumer<T> success, Consumer<Throwable> failure) {
        if (button != null) {
            button.setDisable(true);
        }
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            if (button != null) {
                button.setDisable(false);
            }
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (button != null) {
                button.setDisable(false);
            }
            failure.accept(task.getException());
        });
        Thread.ofVirtual().name("gymflow-members").start(task);
    }

}
