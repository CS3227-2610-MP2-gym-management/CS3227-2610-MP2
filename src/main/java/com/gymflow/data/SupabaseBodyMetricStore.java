package com.gymflow.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.model.BodyMetric;

/** Supabase-backed Member body metric operations. */
public final class SupabaseBodyMetricStore {
    private static final String FIELDS = "id,member_account_id,measurement_date,weight_grams,"
            + "created_at,updated_at";
    private final SupabaseDataClient client;

    /** Creates the store. */
    public SupabaseBodyMetricStore(SupabaseDataClient client) {
        this.client = client;
    }

    /** Lists a Member's metrics. */
    public List<BodyMetric> findByMember(long memberId) {
        JsonNode rows = client.get("body_metrics?select=" + FIELDS + "&member_account_id=eq."
                + memberId + "&order=measurement_date.desc");
        return StreamSupport.stream(rows.spliterator(), false)
                .map(SupabaseRows::bodyMetric).toList();
    }

    /** Creates a metric. */
    public BodyMetric create(long memberId, LocalDate date, long grams, Instant now) {
        JsonNode rows = client.post("body_metrics?select=" + FIELDS,
                Map.of("member_account_id", memberId, "measurement_date", date.toString(),
                        "weight_grams", grams));
        return SupabaseRows.bodyMetric(single(rows));
    }

    /** Updates a metric. */
    public BodyMetric update(long memberId, long id, LocalDate date, long grams, Instant now) {
        JsonNode rows = client.patch("body_metrics?id=eq." + id + "&member_account_id=eq."
                + memberId + "&select=" + FIELDS,
                Map.of("measurement_date", date.toString(), "weight_grams", grams,
                        "updated_at", now.toString()));
        return SupabaseRows.bodyMetric(single(rows));
    }

    /** Deletes a metric. */
    public void delete(long memberId, long id) {
        JsonNode rows = client.delete("body_metrics?id=eq." + id
                + "&member_account_id=eq." + memberId + "&select=id");
        single(rows);
    }

    private static JsonNode single(JsonNode rows) {
        if (!rows.isArray() || rows.size() != 1) {
            throw new IllegalArgumentException("Body metric was not found or could not be changed");
        }
        return rows.get(0);
    }
}
