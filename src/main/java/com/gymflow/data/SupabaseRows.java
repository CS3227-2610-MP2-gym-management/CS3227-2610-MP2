package com.gymflow.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.model.Announcement;
import com.gymflow.model.BodyMetric;
import com.gymflow.model.Expense;
import com.gymflow.model.ExpenseCategory;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.Membership;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.Visit;
import com.gymflow.model.WorkoutSet;

/** Converts Data API rows into transport-free GymFlow domain records. */
final class SupabaseRows {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);

    private SupabaseRows() {
    }

    static Announcement announcement(JsonNode row) {
        return new Announcement(row.path("id").asLong(), required(row, "title"),
                required(row, "content"), instant(row, "published_at"),
                row.path("created_by_account_id").asLong(), nullableInstant(row, "withdrawn_at"),
                instant(row, "created_at"), instant(row, "updated_at"));
    }

    static BodyMetric bodyMetric(JsonNode row) {
        return new BodyMetric(row.path("id").asLong(), row.path("member_account_id").asLong(),
                LocalDate.parse(required(row, "measurement_date")),
                BigDecimal.valueOf(row.path("weight_grams").asLong()).divide(ONE_THOUSAND),
                instant(row, "created_at"), instant(row, "updated_at"));
    }

    static Expense expense(JsonNode row) {
        return new Expense(row.path("id").asLong(),
                LocalDate.parse(required(row, "expense_date")),
                cents(row.path("amount_cents").asLong()),
                PaymentMethod.valueOf(required(row, "method")),
                ExpenseCategory.valueOf(required(row, "category")),
                row.path("description").asText(""),
                row.path("recorded_by_account_id").asLong(), instant(row, "created_at"));
    }

    static Member member(JsonNode profile) {
        JsonNode account = profile.path("accounts");
        return new Member(profile.path("account_id").asLong(), required(profile, "member_number"),
                required(account, "email"), required(profile, "full_name"),
                required(profile, "phone_number"), nullableDate(profile, "date_of_birth"));
    }

    static Membership membership(JsonNode row) {
        return new Membership(row.path("id").asLong(), row.path("member_account_id").asLong(),
                LocalDate.parse(required(row, "start_date")),
                LocalDate.parse(required(row, "expiry_date")), row.path("is_active").asBoolean(),
                instant(row, "created_at"), instant(row, "updated_at"));
    }

    static MemberPayment payment(JsonNode row) {
        return new MemberPayment(row.path("id").asLong(), row.path("membership_id").asLong(),
                cents(row.path("amount_cents").asLong()),
                PaymentMethod.valueOf(required(row, "method")), instant(row, "paid_at"),
                nullableText(row, "reference"), row.path("recorded_by_account_id").asLong(),
                instant(row, "created_at"));
    }

    static Visit visit(JsonNode row) {
        return new Visit(row.path("id").asLong(), row.path("member_account_id").asLong(),
                instant(row, "entered_at"), nullableInstant(row, "exited_at"),
                instant(row, "created_at"), nullableInstant(row, "corrected_at"),
                nullableLong(row, "corrected_by_account_id"), nullableText(row, "correction_reason"));
    }

    static WorkoutSet workoutSet(JsonNode row) {
        JsonNode resistance = row.path("resistance_grams");
        return new WorkoutSet(row.path("id").asLong(), row.path("workout_id").asLong(),
                row.path("position").asInt(), required(row, "exercise_name"),
                nullableInteger(row, "repetitions"), nullableInteger(row, "duration_seconds"),
                resistance.isNull() || resistance.isMissingNode() ? null
                        : BigDecimal.valueOf(resistance.asLong()).divide(ONE_THOUSAND),
                instant(row, "created_at"), instant(row, "updated_at"));
    }

    static String required(JsonNode row, String field) {
        String value = row.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("GymFlow response is missing " + field);
        }
        return value;
    }

    static Instant instant(JsonNode row, String field) {
        return Instant.parse(required(row, field));
    }

    static Instant nullableInstant(JsonNode row, String field) {
        String value = nullableText(row, field);
        return value == null ? null : Instant.parse(value);
    }

    static String nullableText(JsonNode row, String field) {
        JsonNode value = row.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static BigDecimal cents(long value) {
        return BigDecimal.valueOf(value).divide(ONE_HUNDRED);
    }

    private static LocalDate nullableDate(JsonNode row, String field) {
        String value = nullableText(row, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private static Long nullableLong(JsonNode row, String field) {
        JsonNode value = row.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asLong();
    }

    private static Integer nullableInteger(JsonNode row, String field) {
        JsonNode value = row.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asInt();
    }
}
