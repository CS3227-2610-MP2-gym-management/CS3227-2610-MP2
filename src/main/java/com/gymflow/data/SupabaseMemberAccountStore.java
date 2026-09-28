package com.gymflow.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.model.Member;
import com.gymflow.model.Membership;

/** Supabase-backed Member profile and Membership operations. */
public final class SupabaseMemberAccountStore {
    private static final String PROFILE_FIELDS = "account_id,member_number,full_name,phone_number,"
            + "date_of_birth,accounts!inner(email)";
    private static final String MEMBERSHIP_FIELDS = "id,member_account_id,start_date,expiry_date,"
            + "is_active,created_at,updated_at";
    private final SupabaseDataClient client;

    /** Creates the store. */
    public SupabaseMemberAccountStore(SupabaseDataClient client) {
        this.client = client;
    }

    /** Loads a Member profile. */
    public Member profile(long accountId) {
        JsonNode rows = client.get("member_profiles?select=" + PROFILE_FIELDS
                + "&account_id=eq." + accountId + "&limit=1");
        if (!rows.isArray() || rows.size() != 1) {
            throw new IllegalArgumentException("Member profile was not found");
        }
        return SupabaseRows.member(rows.get(0));
    }

    /** Lists a Member's Membership history. */
    public List<Membership> membershipHistory(long accountId) {
        JsonNode rows = client.get("memberships?select=" + MEMBERSHIP_FIELDS
                + "&member_account_id=eq." + accountId + "&order=start_date.desc,id.desc");
        return StreamSupport.stream(rows.spliterator(), false)
                .map(SupabaseRows::membership).toList();
    }

    /** Updates the current Member's email and phone through the protected account function. */
    public void updateContact(long accountId, String email, String phoneNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", "update");
        body.put("member_account_id", accountId);
        body.put("email", email);
        body.put("phone_number", phoneNumber);
        client.function("manage-member", body);
    }
}
