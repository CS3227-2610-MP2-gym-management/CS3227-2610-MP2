package com.gymflow.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.model.Account;
import com.gymflow.model.Announcement;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Owner publication and withdrawal interface for gym announcements. */
final class OwnerAnnouncementsView {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private OwnerAnnouncementsView() {
    }

    static Parent create(OwnerAnnouncementService announcements, Account owner,
            Consumer<Screen> navigate, Consumer<Node> resetGymFlow, Runnable logout) {
        BorderPane root = new BorderPane();
        root.setId("owner-announcements-screen");
        root.setLeft(UiComponents.ownerSidebar("Announcements", navigate, resetGymFlow, logout));
        showList(root, announcements, owner);
        return root;
    }

    private static void showList(BorderPane root, OwnerAnnouncementService service, Account owner) {
        Tab publishedTab = new Tab("Published");
        Tab withdrawnTab = new Tab("Withdrawn");
        TabPane tabs = new TabPane(publishedTab, withdrawnTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Runnable[] refresh = new Runnable[1];
        publishedTab.setContent(listContent(root, service, owner, false, refresh));
        withdrawnTab.setContent(listContent(root, service, owner, true, refresh));
        Button publish = new Button("Publish Announcement");
        publish.getStyleClass().add("primary-button");
        publish.setOnAction(event -> showPublish(publish, service, owner, refresh[0]));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox content = new VBox(20,
                UiComponents.header("Announcements", "Publish notices for gym Members", publish), tabs);
        content.setPadding(new Insets(36));
        root.setCenter(content);
        Platform.runLater(refresh[0]);
    }

    private static VBox listContent(BorderPane root, OwnerAnnouncementService service, Account owner,
            boolean withdrawn, Runnable[] sharedRefresh) {
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        UiComponents.collapseWhenEmpty(error);
        ListView<Announcement> list = announcementList(withdrawn, announcement ->
                showDetail(root, service, owner, announcement));
        long[] version = {0};
        Runnable refresh = () -> {
            long request = ++version[0];
            error.setText("");
            OwnerMembersView.run(null, withdrawn ? service::listWithdrawn : service::listPublished,
                    result -> {
                        if (request == version[0]) {
                            list.getItems().setAll(result);
                        }
                    }, exception -> error.setText("Unable to access GymFlow data"));
        };
        Runnable previous = sharedRefresh[0];
        sharedRefresh[0] = previous == null ? refresh : () -> {
            previous.run();
            refresh.run();
        };
        VBox content = new VBox(12, error, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        return content;
    }

    private static ListView<Announcement> announcementList(boolean withdrawn,
            Consumer<Announcement> open) {
        return UiComponents.cardList(withdrawn
                ? "No withdrawn Announcements" : "No published Announcements", item -> {
                    Label title = UiComponents.cardLabel(item.title(), "record-title");
                    Label date = UiComponents.cardLabel("Published "
                            + DATE_TIME.format(item.publishedAt()), "record-meta");
                    String detail = withdrawn
                            ? "Withdrawn " + DATE_TIME.format(item.withdrawnAt())
                            : preview(item);
                    VBox card = new VBox(6, title, date,
                            UiComponents.cardLabel(detail, "record-value"));
                    card.getStyleClass().add("record-card");
                    UiComponents.makeActionable(card, "Open announcement " + item.title(),
                            () -> open.accept(item));
                    return card;
                });
    }

    private static void showDetail(BorderPane root, OwnerAnnouncementService service,
            Account owner, Announcement announcement) {
        Button back = new Button("Back to Announcements");
        back.getStyleClass().add("secondary-button");
        back.setOnAction(event -> showList(root, service, owner));
        Button withdraw = new Button("Withdraw");
        withdraw.getStyleClass().add("danger-button");
        withdraw.setVisible(announcement.withdrawnAt() == null);
        withdraw.setManaged(announcement.withdrawnAt() == null);
        withdraw.setOnAction(event -> confirmWithdraw(withdraw, service, owner, announcement,
                () -> showList(root, service, owner)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(12, back, spacer, withdraw);
        Label content = new Label(announcement.content());
        content.setWrapText(true);
        content.getStyleClass().add("detail-text");
        Label metadata = new Label("Published " + DATE_TIME.format(announcement.publishedAt())
                + (announcement.withdrawnAt() == null ? ""
                        : "  •  Withdrawn " + DATE_TIME.format(announcement.withdrawnAt())));
        metadata.getStyleClass().add("muted-text");
        VBox body = UiComponents.card(metadata, content);
        VBox page = new VBox(20, actions,
                UiComponents.header(announcement.title(), "Announcement details", null), body);
        page.setPadding(new Insets(36));
        root.setCenter(UiComponents.scrollable(page));
    }

    private static void showPublish(Node ownerNode, OwnerAnnouncementService service,
            Account owner, Runnable refresh) {
        TextField title = new TextField();
        title.setPromptText("Announcement title");
        TextArea content = new TextArea();
        content.setPromptText("Announcement content");
        content.setWrapText(true);
        content.setPrefRowCount(7);
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        Label heading = new Label("Publish Announcement");
        heading.getStyleClass().add("dialog-title");
        UiComponents.preserveLabelHeight(heading);
        Label titleLabel = new Label("Title");
        Label contentLabel = new Label("Content");
        UiComponents.preserveLabelHeight(titleLabel);
        UiComponents.preserveLabelHeight(contentLabel);
        VBox fields = new VBox(12, heading, error, titleLabel, title, contentLabel, content);
        fields.getStyleClass().add("dialog-content");
        ButtonType publishType = new ButtonType("Publish Announcement", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Publish Announcement");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, publishType);
        dialog.getDialogPane().setContent(fields);
        UiComponents.styleDialog(dialog, ownerNode, "membership-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(publishType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        submit.getStyleClass().add("primary-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            cancel.setDisable(true);
            submit.setText("Publishing…");
            OwnerMembersView.run(submit, () -> service.publish(title.getText(), content.getText(), owner.id()),
                    saved -> {
                        dialog.close();
                        refresh.run();
                    }, exception -> {
                        cancel.setDisable(false);
                        submit.setText("Publish Announcement");
                        error.setText(exception instanceof IllegalArgumentException
                                ? exception.getMessage() : "Unable to access GymFlow data");
                        (error.getText().toLowerCase(Locale.ENGLISH).contains("title")
                                ? title : content).requestFocus();
                    });
        });
        dialog.showAndWait();
    }

    private static void confirmWithdraw(Node ownerNode, OwnerAnnouncementService service,
            Account owner, Announcement announcement, Runnable completed) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Withdraw Announcement");
        Label message = new Label("Withdraw “" + announcement.title()
                + "”? Members will no longer see it, but its history will be retained.");
        message.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        UiComponents.preserveLabelHeight(error);
        VBox content = new VBox(12, message, error);
        content.getStyleClass().add("dialog-content");
        ButtonType withdrawType = new ButtonType("Withdraw", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, withdrawType);
        dialog.getDialogPane().setContent(content);
        UiComponents.styleDialog(dialog, ownerNode, "membership-dialog", false);
        Button submit = (Button) dialog.getDialogPane().lookupButton(withdrawType);
        submit.getStyleClass().add("danger-button");
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            error.setText("");
            OwnerMembersView.run(submit, () -> service.withdraw(announcement.id(), owner.id()), saved -> {
                dialog.close();
                completed.run();
            }, exception -> {
                submit.setDisable(false);
                error.setText(exception instanceof IllegalArgumentException
                        ? exception.getMessage() : "Unable to access GymFlow data");
            });
        });
        dialog.showAndWait();
    }

    private static String preview(Announcement announcement) {
        String content = announcement.content().replaceAll("\\s+", " ");
        return content.length() <= 80 ? content : content.substring(0, 77) + "…";
    }

}
