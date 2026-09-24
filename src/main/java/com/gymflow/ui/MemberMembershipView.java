package com.gymflow.ui;

import java.util.function.Consumer;

import com.gymflow.member.MemberAccountService;
import com.gymflow.model.Account;
import com.gymflow.model.Membership;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

/** Displays complete Membership history for the authenticated Member. */
final class MemberMembershipView {
    private MemberMembershipView() {
    }

    static Parent create(MemberAccountService accounts, Account session,
            Consumer<Screen> navigate, Runnable logout) {
        ListView<Membership> history = UiComponents.cardList("No Membership history recorded yet.",
                item -> UiComponents.card(UiComponents.cardLabel(
                        MemberHomeView.membershipText(item, accounts.today()), "detail-text")));
        history.setPrefHeight(480);
        Label status = new Label("Loading Membership history…");
        status.getStyleClass().add("muted-text");
        VBox content = new VBox(20, UiComponents.header("My Membership", "Your complete Membership history", null),
                UiComponents.card(status, history));
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(36));
        Thread.startVirtualThread(() -> MemberHomeView.load(accounts, session, overview -> {
            history.getItems().setAll(overview.memberships());
            status.setText(overview.memberships().isEmpty() ? "No Membership periods are recorded."
                    : "Statuses are current as of today.");
        }, status::setText));
        return MemberHomeView.shell(content, Screen.MEMBER_MEMBERSHIP, navigate, logout);
    }
}
