package com.gymflow.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.model.Announcement;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.VBox;

/** Displays published gym announcements to the authenticated Member. */
final class MemberAnnouncementsView {
    private static final int CARD_TITLE_CHARACTER_LIMIT = 100;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private MemberAnnouncementsView() {
    }

    static Parent create(OwnerAnnouncementService announcements, Consumer<Screen> navigate,
            Runnable logout) {
        VBox content = new VBox(20);
        VBox announcementsList = new VBox(10);
        announcementsList.setFillWidth(true);
        Label status = new Label();
        status.getStyleClass().add("muted-text");
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        Runnable load = () -> load(announcements, announcementsList, content, status, refresh);
        refresh.setOnAction(event -> load.run());
        content.getChildren().setAll(
                UiComponents.header("Announcements", "Published notices from your gym", refresh),
                UiComponents.card(status, announcementsList));
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        load.run();
        return MemberHomeView.shell(content, Screen.MEMBER_ANNOUNCEMENTS, navigate, logout);
    }

    private static void load(OwnerAnnouncementService announcements, VBox announcementsList, Node owner,
            Label status, Button refresh) {
        refresh.setDisable(true);
        status.setText("Loading Announcements…");
        Thread.startVirtualThread(() -> {
            try {
                List<Announcement> result = announcements.listPublished();
                Platform.runLater(() -> {
                    if (result.isEmpty()) {
                        announcementsList.getChildren().setAll(
                                UiComponents.emptyState("No published Announcements."));
                    } else {
                        announcementsList.getChildren().setAll(result.stream()
                                .map(announcement -> announcementCard(owner, announcement)).toList());
                    }
                    status.setText(result.isEmpty() ? "No published Announcements are available."
                            : "Published Announcements, newest first.");
                    refresh.setDisable(false);
                });
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    status.setText("Unable to load Announcements.");
                    refresh.setDisable(false);
                });
            }
        });
    }

    private static VBox announcementCard(Node owner, Announcement announcement) {
        VBox card = new VBox(6,
                singleLineLabel(cardTitle(announcement.title()), "record-title"),
                singleLineLabel("Published " + DATE_TIME.format(announcement.publishedAt()), "record-meta"),
                singleLineLabel(preview(announcement.content()), "record-value"));
        card.getStyleClass().add("record-card");
        UiComponents.makeActionable(card, "Open announcement " + announcement.title(),
                () -> showDetail(owner, announcement));
        return card;
    }

    private static Label singleLineLabel(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static void showDetail(Node owner, Announcement announcement) {
        Label title = new Label(announcement.title());
        title.getStyleClass().add("dialog-title");
        UiComponents.preserveLabelHeight(title);
        Label metadata = new Label("Published " + DATE_TIME.format(announcement.publishedAt()));
        metadata.getStyleClass().add("muted-text");
        Label content = new Label(announcement.content());
        content.setWrapText(true);
        content.getStyleClass().add("detail-text");
        VBox details = new VBox(12, title, metadata, content);
        details.getStyleClass().add("dialog-content");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(announcement.title());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(details);
        UiComponents.styleDialog(dialog, owner, "announcement-dialog", true);
        Button close = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        close.setText("Close");
        close.getStyleClass().add("primary-button");
        dialog.show();
    }

    static String preview(String content) {
        String condensed = content.replaceAll("\\s+", " ");
        return condensed.length() <= 80 ? condensed : condensed.substring(0, 77) + "…";
    }

    static String cardTitle(String title) {
        String condensed = title.replaceAll("\\s+", " ");
        return condensed.length() <= CARD_TITLE_CHARACTER_LIMIT ? condensed
                : condensed.substring(0, CARD_TITLE_CHARACTER_LIMIT - 1) + "…";
    }
}
