package com.gymflow.ui;

import java.util.List;
import java.util.function.Consumer;

import com.gymflow.model.Account;
import com.gymflow.model.Visit;
import com.gymflow.visit.MemberVisitService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

/** Displays the authenticated Member's completed and ongoing Visit history. */
final class MemberVisitsView {
    private MemberVisitsView() {
    }

    static Parent create(MemberVisitService visits, Account session,
            Consumer<Screen> navigate, Runnable logout) {
        ListView<Visit> history = UiComponents.cardList("No gym Visits recorded yet.", visit -> {
            VBox card = new VBox(6,
                    UiComponents.cardLabel("Entry: " + VisitFormat.entryTime(visit.enteredAt()),
                            "record-title"),
                    UiComponents.cardLabel("Exit: " + VisitFormat.exitTime(visit.exitedAt()),
                            "record-meta"),
                    UiComponents.cardLabel(visit.exitedAt() == null ? "Currently inside"
                            : "Duration: " + VisitFormat.duration(visit.enteredAt(), visit.exitedAt()),
                            "record-value"));
            card.getStyleClass().add("record-card");
            return card;
        });
        history.setPrefHeight(480);
        Label status = new Label("Loading Visit history…");
        status.getStyleClass().add("muted-text");
        VBox content = new VBox(20, UiComponents.header("Gym Visits", "Your completed and current gym Visits", null),
                UiComponents.card(status, history));
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        Thread.startVirtualThread(() -> load(visits, session, history, status));
        return MemberHomeView.shell(content, Screen.MEMBER_VISITS, navigate, logout);
    }

    private static void load(MemberVisitService visits, Account session,
            ListView<Visit> history, Label status) {
        try {
            List<Visit> result = visits.history(session);
            Platform.runLater(() -> {
                history.getItems().setAll(result);
                status.setText(result.isEmpty() ? "No gym Visits are recorded."
                        : "Your Visit history, newest first.");
            });
        } catch (RuntimeException exception) {
            Platform.runLater(() -> status.setText("Unable to load your Visit history."));
        }
    }
}
