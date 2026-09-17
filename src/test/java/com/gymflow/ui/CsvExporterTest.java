package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.PaymentOverview;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;

class CsvExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsMemberFieldsWithoutMutatingCurrentView() throws IOException {
        Member member = new Member(91, "M000001", "=member@example.com",
                "Zoë, \"Z\"\r\nTan", "+65 9876 5432", null);
        List<Member> displayed = new ArrayList<>(List.of(member));
        Path file = temporaryDirectory.resolve("members.csv");

        CsvExporter.writeMembers(file, displayed);

        assertEquals(List.of(member), displayed);
        assertEquals("Member Number,Full Name,Email,Phone Number,Date of Birth\r\n"
                + "M000001,\"Zoë, \"\"Z\"\"\r\nTan\",'=member@example.com,'+65 9876 5432,\r\n",
                Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void exportsPaymentContextWithTwoDecimalAmountAndSafeReference() throws IOException {
        MemberPayment payment = new MemberPayment(8, 7, new BigDecimal("12"),
                PaymentMethod.CARD, Instant.parse("2026-09-16T05:06:07Z"),
                "@SUM(1,1)", 1, Instant.parse("2026-09-16T05:06:07Z"));
        PaymentOverview overview = new PaymentOverview(payment, "M000002", "Alice",
                "alice@example.com", LocalDate.parse("2026-09-16"),
                LocalDate.parse("2026-10-16"));
        Path file = temporaryDirectory.resolve("payments.csv");

        CsvExporter.writePayments(file, List.of(overview));

        assertEquals("Member Number,Member Name,Member Email,Membership Start,Membership Expiry,"
                + "Amount SGD,Method,Paid At,Reference\r\n"
                + "M000002,Alice,alice@example.com,2026-09-16,2026-10-16,12.00,CARD,"
                + "2026-09-16T05:06:07Z,\"'@SUM(1,1)\"\r\n",
                Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void exportsCompletedAndOpenVisitsWithCorrectionDetails() throws IOException {
        Visit completed = new Visit(4, 2, Instant.parse("2026-09-16T01:00:00Z"),
                Instant.parse("2026-09-16T02:30:00Z"), Instant.parse("2026-09-16T01:00:00Z"),
                Instant.parse("2026-09-16T03:00:00Z"), 1L, "Wrong exit, corrected");
        Visit open = new Visit(5, 3, Instant.parse("2026-09-16T04:00:00Z"), null,
                Instant.parse("2026-09-16T04:00:00Z"), null, null, null);
        Path file = temporaryDirectory.resolve("visits.csv");

        CsvExporter.writeVisits(file, List.of(
                new VisitOverview(completed, "M000003", "Bob", "bob@example.com"),
                new VisitOverview(open, "M000004", "陈美", "mei@example.com")));

        assertEquals("Member Number,Member Name,Member Email,Entered At,Exited At,Duration Minutes,"
                + "Corrected At,Correction Reason\r\n"
                + "M000003,Bob,bob@example.com,2026-09-16T01:00:00Z,2026-09-16T02:30:00Z,90,"
                + "2026-09-16T03:00:00Z,\"Wrong exit, corrected\"\r\n"
                + "M000004,陈美,mei@example.com,2026-09-16T04:00:00Z,,,,\r\n",
                Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void propagatesWriteFailure() {
        Path file = temporaryDirectory.resolve("missing").resolve("members.csv");

        assertThrows(IOException.class, () -> CsvExporter.writeMembers(file, List.of()));
    }

    @Test
    void appendsCsvExtensionOnlyWhenMissing() {
        assertEquals(Path.of("members.csv"), CsvExporter.csvPath(Path.of("members")));
        assertEquals(Path.of("members.CSV"), CsvExporter.csvPath(Path.of("members.CSV")));
    }
}
