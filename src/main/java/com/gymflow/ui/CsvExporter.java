package com.gymflow.ui;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import com.gymflow.model.Member;
import com.gymflow.model.PaymentOverview;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;

/** Writes Owner-visible records as safe, portable CSV files. */
final class CsvExporter {
    private static final String[] MEMBER_HEADERS = {
        "Member Number", "Full Name", "Email", "Phone Number", "Date of Birth"
    };
    private static final String[] PAYMENT_HEADERS = {
        "Member Number", "Member Name", "Member Email", "Membership Start",
        "Membership Expiry", "Amount SGD", "Method", "Paid At", "Reference"
    };
    private static final String[] VISIT_HEADERS = {
        "Member Number", "Member Name", "Member Email", "Entered At", "Exited At",
        "Duration Minutes", "Corrected At", "Correction Reason"
    };

    private CsvExporter() {
    }

    static void writeMembers(Path file, List<Member> members) throws IOException {
        try (BufferedWriter writer = writer(file)) {
            row(writer, MEMBER_HEADERS);
            for (Member member : members) {
                row(writer, member.memberNumber(), member.fullName(), member.email(),
                        member.phoneNumber(), value(member.dateOfBirth()));
            }
        }
    }

    static void writePayments(Path file, List<PaymentOverview> payments) throws IOException {
        try (BufferedWriter writer = writer(file)) {
            row(writer, PAYMENT_HEADERS);
            for (PaymentOverview overview : payments) {
                var payment = overview.payment();
                row(writer, overview.memberNumber(), overview.memberName(), overview.memberEmail(),
                        value(overview.membershipStart()), value(overview.membershipExpiry()),
                        payment.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        payment.method().name(), payment.paidAt().toString(), payment.reference());
            }
        }
    }

    static void writeVisits(Path file, List<VisitOverview> visits) throws IOException {
        try (BufferedWriter writer = writer(file)) {
            row(writer, VISIT_HEADERS);
            for (VisitOverview overview : visits) {
                Visit visit = overview.visit();
                String duration = visit.exitedAt() == null ? ""
                        : Long.toString(Duration.between(visit.enteredAt(), visit.exitedAt()).toMinutes());
                row(writer, overview.memberNumber(), overview.memberName(), overview.memberEmail(),
                        visit.enteredAt().toString(), value(visit.exitedAt()), duration,
                        value(visit.correctedAt()), visit.correctionReason());
            }
        }
    }

    static Path csvPath(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")
                ? file : Path.of(file + ".csv");
    }

    private static BufferedWriter writer(Path file) throws IOException {
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    }

    private static void row(BufferedWriter writer, String... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(cell(values[index]));
        }
        writer.write("\r\n");
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
