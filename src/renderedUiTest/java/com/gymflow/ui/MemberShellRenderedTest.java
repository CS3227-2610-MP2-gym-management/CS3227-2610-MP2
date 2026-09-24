package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

/** Verifies Member layout after JavaFX has rendered it in a real window. */
class MemberShellRenderedTest {
    @Test
    void memberShellProvidesAWorkingVerticalScrollContainer() throws Exception {
        JavaFxTestSupport.call(() -> {
            VBox content = new VBox();
            content.setMinHeight(1_200);
            BorderPane root = MemberHomeView.shell(content, Screen.MEMBER_HOME, ignored -> { }, () -> { });
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(root, 900, 500));
                stage.show();
                root.applyCss();
                root.layout();

                ScrollPane scroll = (ScrollPane) root.getCenter();
                assertEquals("member-member_home-scroll", scroll.getId());
                assertSame(content, scroll.getContent());
                assertTrue(scroll.isFitToWidth());
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getVbarPolicy());
                assertTrue(scroll.getVmax() > scroll.getVmin());
                scroll.setVvalue(scroll.getVmax());
                assertEquals(scroll.getVmax(), scroll.getVvalue());
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void longMemberDetailTextWrapsAfterLayout() throws Exception {
        JavaFxTestSupport.call(() -> {
            Label detail = MemberHomeView.detail("Visit the gym in person to purchase or renew your Membership.");
            VBox card = UiComponents.card(detail);
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(card, 280, 200));
                stage.show();
                card.applyCss();
                card.layout();

                assertTrue(detail.isWrapText());
                assertTrue(detail.getWidth() < detail.prefWidth(-1));
                assertTrue(detail.getHeight() > detail.prefHeight(-1));
            } finally {
                stage.close();
            }
            return null;
        });
    }
}
