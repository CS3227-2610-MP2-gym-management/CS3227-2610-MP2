package com.gymflow.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.model.MemberVisitState;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;

/** Supabase-backed Member and Owner Visit operations. */
public final class SupabaseVisitStore {
    private static final String FIELDS = "id,member_account_id,entered_at,exited_at,created_at,"
            + "corrected_at,corrected_by_account_id,correction_reason";
    private static final String OVERVIEW_FIELDS = FIELDS
            + ",member_profiles!inner(member_number,full_name,accounts!inner(email))";
    private final SupabaseDataClient client;

    /** Creates the store. */
    public SupabaseVisitStore(SupabaseDataClient client) {
        this.client = client;
    }

    /** Loads the current Member Visit state. */
    public MemberVisitState currentState(long memberId) {
        JsonNode rows = client.get("visits?select=" + FIELDS + "&member_account_id=eq."
                + memberId + "&exited_at=is.null&limit=1");
        return new MemberVisitState(rows.isEmpty() ? null : SupabaseRows.visit(rows.get(0)));
    }

    /** Checks in the current Member through the protected database operation. */
    public Visit checkIn(long memberId, LocalDate date, Instant now) {
        return SupabaseRows.visit(client.rpc("member_check_in", Map.of()));
    }

    /** Checks out the current Member through the protected database operation. */
    public Visit checkOut(long memberId, Instant now) {
        return SupabaseRows.visit(client.rpc("member_check_out", Map.of()));
    }

    /** Lists one Member's Visit history. */
    public List<Visit> history(long memberId) {
        JsonNode rows = client.get("visits?select=" + FIELDS + "&member_account_id=eq."
                + memberId + "&order=entered_at.desc");
        return StreamSupport.stream(rows.spliterator(), false).map(SupabaseRows::visit).toList();
    }

    /** Searches visible Visits. */
    public List<VisitOverview> search(String query, boolean currentlyVisitingOnly) {
        String current = currentlyVisitingOnly ? "&exited_at=is.null" : "";
        JsonNode rows = client.get("visits?select=" + OVERVIEW_FIELDS + current
                + "&order=entered_at.desc");
        String normalized = query.toLowerCase(Locale.ROOT);
        return StreamSupport.stream(rows.spliterator(), false)
                .map(this::overview)
                .filter(item -> normalized.isEmpty() || contains(item.memberName(), normalized)
                        || contains(item.memberEmail(), normalized)
                        || contains(item.memberNumber(), normalized))
                .toList();
    }

    /** Returns the number of currently open Visits. */
    public long countCurrentlyVisiting() {
        return client.get("visits?select=id&exited_at=is.null").size();
    }

    /** Corrects a Visit through the protected Owner operation. */
    public Visit correct(long visitId, Instant enteredAt, Instant exitedAt,
            String reason, long ownerAccountId) {
        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        arguments.put("p_visit_id", visitId);
        arguments.put("p_entered_at", enteredAt.toString());
        arguments.put("p_exited_at", exitedAt == null ? null : exitedAt.toString());
        arguments.put("p_reason", reason);
        return SupabaseRows.visit(client.rpc("owner_correct_visit", arguments));
    }

    private VisitOverview overview(JsonNode row) {
        JsonNode profile = row.path("member_profiles");
        return new VisitOverview(SupabaseRows.visit(row),
                SupabaseRows.required(profile, "member_number"),
                SupabaseRows.required(profile, "full_name"),
                SupabaseRows.required(profile.path("accounts"), "email"));
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }
}
