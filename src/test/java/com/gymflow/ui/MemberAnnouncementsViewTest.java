package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MemberAnnouncementsViewTest {
    @Test
    void makesSingleLinePreviewAndPreservesShortContent() {
        assertEquals("First line second line", MemberAnnouncementsView.preview("First line\nsecond line"));
        assertEquals("a".repeat(77) + "…", MemberAnnouncementsView.preview("a".repeat(81)));
    }

    @Test
    void limitsCardTitlesToTwoLinesWorthOfCharacters() {
        assertEquals("A title", MemberAnnouncementsView.cardTitle("A title"));
        assertEquals("a".repeat(99) + "…", MemberAnnouncementsView.cardTitle("a".repeat(101)));
    }

    @Test
    void identifiesAnnouncementsAsAMemberScreen() {
        assertEquals("Announcements", MemberHomeView.title(Screen.MEMBER_ANNOUNCEMENTS));
        assertEquals("Announcements", MemberHomeView.navigationItem(Screen.MEMBER_ANNOUNCEMENTS));
    }
}
