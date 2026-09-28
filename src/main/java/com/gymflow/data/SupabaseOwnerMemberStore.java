package com.gymflow.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.member.AddMembershipRequest;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.Membership;
import com.gymflow.model.MembershipOverview;
import com.gymflow.model.OwnerDashboard;
import com.gymflow.model.PaymentOverview;

/** Supabase-backed Owner Member, Membership, and Payment operations. */
public final class SupabaseOwnerMemberStore {
    private static final String PROFILE_FIELDS = "account_id,member_number,full_name,phone_number,"
            + "date_of_birth,accounts!inner(email)";
    private static final String MEMBERSHIP_FIELDS = "id,member_account_id,start_date,expiry_date,"
            + "is_active,created_at,updated_at";
    private static final String PAYMENT_FIELDS = "id,membership_id,amount_cents,method,paid_at,"
            + "reference,recorded_by_account_id,created_at";
    private final SupabaseDataClient client;

    /** Creates the store. */
    public SupabaseOwnerMemberStore(SupabaseDataClient client) {
        this.client = client;
    }

    /** Atomically provisions a login and its initial Member records. */
    public Member create(CreateMemberRequest request, String email, String phoneNumber,
            long ownerAccountId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", "create");
        body.put("email", email);
        body.put("password", new String(request.initialPassword()));
        body.put("full_name", request.fullName().trim());
        body.put("phone_number", phoneNumber);
        body.put("date_of_birth", request.dateOfBirth() == null ? null : request.dateOfBirth().toString());
        body.put("membership_start", request.membershipStart().toString());
        body.put("membership_expiry", request.membershipExpiry().toString());
        body.put("payment_amount_cents", cents(request.paymentAmount()));
        body.put("payment_method", request.paymentMethod().name());
        body.put("paid_at", request.paidAt().toString());
        body.put("payment_reference", blankToNull(request.paymentReference()));
        long memberId = client.function("manage-member", body).path("member_account_id").asLong();
        return profile(memberId);
    }

    /** Replaces a Member password through the protected account function. */
    public void updatePassword(long memberAccountId, char[] password, long ownerAccountId) {
        try {
            client.function("manage-member", Map.of("action", "reset-password",
                    "member_account_id", memberAccountId, "password", new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Searches Member profiles. */
    public List<Member> search(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        JsonNode rows = client.get("member_profiles?select=" + PROFILE_FIELDS
                + "&order=full_name.asc,member_number.asc");
        return StreamSupport.stream(rows.spliterator(), false).map(SupabaseRows::member)
                .filter(member -> normalized.isEmpty() || contains(member.fullName(), normalized)
                        || contains(member.email(), normalized))
                .toList();
    }

    /** Lists one Member's Payments. */
    public List<MemberPayment> paymentHistory(long memberAccountId) {
        JsonNode rows = client.get("payments?select=" + PAYMENT_FIELDS
                + ",memberships!inner(member_account_id)&memberships.member_account_id=eq."
                + memberAccountId + "&order=paid_at.desc,id.desc");
        return StreamSupport.stream(rows.spliterator(), false).map(SupabaseRows::payment).toList();
    }

    /** Searches all Payments with Member and Membership context. */
    public List<PaymentOverview> searchPayments(String query) {
        String nested = "memberships!inner(start_date,expiry_date,member_account_id,"
                + "member_profiles!inner(member_number,full_name,accounts!inner(email)))";
        JsonNode rows = client.get("payments?select=" + PAYMENT_FIELDS + "," + nested
                + "&order=paid_at.desc,id.desc");
        String normalized = query.toLowerCase(Locale.ROOT);
        return StreamSupport.stream(rows.spliterator(), false).map(this::paymentOverview)
                .filter(item -> normalized.isEmpty() || contains(item.memberName(), normalized)
                        || contains(item.memberEmail(), normalized))
                .toList();
    }

    /** Builds the Owner dashboard from visible cloud rows. */
    public OwnerDashboard ownerDashboard(LocalDate today) {
        List<MembershipOverview> memberships = searchMemberships("");
        long active = memberships.stream().map(MembershipOverview::membership)
                .filter(item -> item.active() && !today.isBefore(item.startDate())
                        && !today.isAfter(item.expiryDate()))
                .count();
        BigDecimal income = searchPayments("").stream()
                .map(item -> item.payment().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OwnerDashboard(search("").size(), active, income, memberships);
    }

    /** Lists one Member's Membership history. */
    public List<Membership> membershipHistory(long memberAccountId) {
        JsonNode rows = client.get("memberships?select=" + MEMBERSHIP_FIELDS
                + "&member_account_id=eq." + memberAccountId
                + "&order=start_date.desc,id.desc");
        return StreamSupport.stream(rows.spliterator(), false)
                .map(SupabaseRows::membership).toList();
    }

    /** Searches Memberships with Member identity context. */
    public List<MembershipOverview> searchMemberships(String query) {
        String nested = "member_profiles!inner(member_number,full_name,accounts!inner(email))";
        JsonNode rows = client.get("memberships?select=" + MEMBERSHIP_FIELDS + "," + nested
                + "&order=start_date.desc,id.desc");
        String normalized = query.toLowerCase(Locale.ROOT);
        return StreamSupport.stream(rows.spliterator(), false).map(this::membershipOverview)
                .filter(item -> normalized.isEmpty() || contains(item.memberName(), normalized)
                        || contains(item.memberEmail(), normalized))
                .toList();
    }

    /** Atomically adds a Membership and Payment. */
    public Membership addMembership(AddMembershipRequest request, long ownerAccountId) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("p_member_account_id", request.memberId());
        arguments.put("p_start_date", request.startDate().toString());
        arguments.put("p_expiry_date", request.expiryDate().toString());
        arguments.put("p_amount_cents", cents(request.paymentAmount()));
        arguments.put("p_method", request.paymentMethod().name());
        arguments.put("p_paid_at", request.paidAt().toString());
        arguments.put("p_reference", blankToNull(request.paymentReference()));
        return SupabaseRows.membership(client.rpc("owner_add_membership", arguments));
    }

    /** Activates or deactivates a Membership. */
    public Membership setMembershipActive(long membershipId, boolean active,
            long ownerAccountId) {
        return SupabaseRows.membership(client.rpc("owner_set_membership_active",
                Map.of("p_membership_id", membershipId, "p_active", active)));
    }

    /** Checks whether the Member has access on a date. */
    public boolean hasValidMembership(long memberAccountId, LocalDate date) {
        return membershipHistory(memberAccountId).stream().anyMatch(item -> item.active()
                && !date.isBefore(item.startDate()) && !date.isAfter(item.expiryDate()));
    }

    /** Updates a Member profile and login email. */
    public Member update(long accountId, String email, String fullName,
            String phoneNumber, LocalDate dateOfBirth) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", "update");
        body.put("member_account_id", accountId);
        body.put("email", email);
        body.put("full_name", fullName);
        body.put("phone_number", phoneNumber);
        body.put("date_of_birth", dateOfBirth == null ? null : dateOfBirth.toString());
        client.function("manage-member", body);
        return profile(accountId);
    }

    private Member profile(long id) {
        JsonNode rows = client.get("member_profiles?select=" + PROFILE_FIELDS
                + "&account_id=eq." + id + "&limit=1");
        return SupabaseRows.member(single(rows, "Member"));
    }

    private MembershipOverview membershipOverview(JsonNode row) {
        JsonNode profile = row.path("member_profiles");
        return new MembershipOverview(SupabaseRows.membership(row),
                SupabaseRows.required(profile, "member_number"),
                SupabaseRows.required(profile, "full_name"),
                SupabaseRows.required(profile.path("accounts"), "email"));
    }

    private PaymentOverview paymentOverview(JsonNode row) {
        JsonNode membership = row.path("memberships");
        JsonNode profile = membership.path("member_profiles");
        return new PaymentOverview(SupabaseRows.payment(row),
                SupabaseRows.required(profile, "member_number"),
                SupabaseRows.required(profile, "full_name"),
                SupabaseRows.required(profile.path("accounts"), "email"),
                LocalDate.parse(SupabaseRows.required(membership, "start_date")),
                LocalDate.parse(SupabaseRows.required(membership, "expiry_date")));
    }

    private static long cents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static JsonNode single(JsonNode rows, String name) {
        if (!rows.isArray() || rows.size() != 1) {
            throw new IllegalArgumentException(name + " was not found or could not be changed");
        }
        return rows.get(0);
    }
}
