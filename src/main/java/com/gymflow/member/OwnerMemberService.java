package com.gymflow.member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.gymflow.auth.PasswordHash;
import com.gymflow.auth.PasswordHasher;
import com.gymflow.auth.AccountValidation;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.OwnerMemberStore;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.Membership;
import com.gymflow.model.MembershipOverview;
import com.gymflow.model.PaymentOverview;
import com.gymflow.model.OwnerDashboard;

/** Implements Owner-side Member onboarding and profile management. */
public final class OwnerMemberService {
    private final OwnerMemberStore members;
    private final PasswordHasher passwords = new PasswordHasher();

    /** Creates a Member service backed by the supplied database. */
    public OwnerMemberService(GymFlowDatabase database) {
        members = new OwnerMemberStore(database);
    }

    /** Atomically creates a Member account, profile, Membership, and Payment. */
    public Member createMember(CreateMemberRequest request, long ownerAccountId) {
        char[] password = request == null ? null : request.initialPassword();
        try {
            validate(request);
            String email = AccountValidation.normalizeEmail(request.email());
            String phone = AccountValidation.normalizePhone(request.phoneNumber());
            PasswordHash hash = passwords.hash(password);
            return members.create(request, email, phone, hash, ownerAccountId);
        } finally {
            clear(password);
        }
    }

    /** Replaces a Member's password when requested by an active Owner. */
    public void resetMemberPassword(long memberAccountId, char[] newPassword,
            long ownerAccountId) {
        try {
            AccountValidation.validatePassword(newPassword);
            members.updatePassword(memberAccountId, passwords.hash(newPassword), ownerAccountId);
        } finally {
            clear(newPassword);
        }
    }

    /** Searches all Members when the query is blank, otherwise matches name or email. */
    public List<Member> searchMembers(String query) {
        return members.search(query == null ? "" : query.trim());
    }

    /** Lists payments recorded for a Member. */
    public List<MemberPayment> paymentHistory(long memberAccountId) {
        return members.paymentHistory(memberAccountId);
    }

    /** Searches all Payments by Member name or email. */
    public List<PaymentOverview> searchPayments(String query) {
        return members.searchPayments(query == null ? "" : query.trim());
    }

    /** Loads the current Owner overview. */
    public OwnerDashboard ownerDashboard() {
        LocalDate today = LocalDate.now();
        return members.ownerDashboard(today);
    }

    /** Lists one Member's Membership history. */
    public List<Membership> membershipHistory(long memberAccountId) {
        return members.membershipHistory(memberAccountId);
    }

    /** Searches Memberships by Member name or email. */
    public List<MembershipOverview> searchMemberships(String query) {
        return members.searchMemberships(query == null ? "" : query.trim());
    }

    /** Atomically creates one Membership and its Payment. */
    public Membership addMembership(AddMembershipRequest request, long ownerAccountId) {
        validate(request);
        return members.addMembership(request, ownerAccountId);
    }

    /** Activates or deactivates a Membership. */
    public Membership setMembershipActive(long membershipId, boolean active,
            long ownerAccountId) {
        return members.setMembershipActive(membershipId, active, ownerAccountId);
    }

    /** Returns whether a Member may access the gym on the supplied date. */
    public boolean hasValidMembership(long memberAccountId, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Membership date is required");
        }
        return members.hasValidMembership(memberAccountId, date);
    }

    /** Updates editable account and profile fields. */
    public Member updateMember(long accountId, String email, String fullName,
            String phoneNumber, LocalDate dateOfBirth) {
        String normalizedEmail = AccountValidation.normalizeEmail(email);
        requireText(fullName, "Full name is required");
        String normalizedPhone = AccountValidation.normalizePhone(phoneNumber);
        validateDateOfBirth(dateOfBirth);
        return members.update(accountId, normalizedEmail, fullName.trim(), normalizedPhone, dateOfBirth);
    }

    private static void validate(CreateMemberRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Member details are required");
        }
        AccountValidation.normalizeEmail(request.email());
        AccountValidation.validatePassword(request.initialPassword());
        requireText(request.fullName(), "Full name is required");
        AccountValidation.normalizePhone(request.phoneNumber());
        validateDateOfBirth(request.dateOfBirth());
        if (request.membershipStart() == null || request.membershipExpiry() == null
                || request.membershipExpiry().isBefore(request.membershipStart())) {
            throw new IllegalArgumentException("Membership expiry must not precede its start");
        }
        validateAmount(request.paymentAmount());
        if (request.paymentMethod() == null || request.paidAt() == null) {
            throw new IllegalArgumentException("Payment method and time are required");
        }
    }

    private static void validate(AddMembershipRequest request) {
        if (request == null || request.startDate() == null || request.expiryDate() == null) {
            throw new IllegalArgumentException("Membership dates are required");
        }
        if (request.expiryDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Membership expiry must not precede its start");
        }
        validateAmount(request.paymentAmount());
        if (request.paymentMethod() == null || request.paidAt() == null) {
            throw new IllegalArgumentException("Payment method and time are required");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Payment amount must be positive with at most two decimal places");
        }
        try {
            amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Payment amount is too large", exception);
        }
    }


    private static void validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now().minusYears(12))) {
            throw new IllegalArgumentException("Member must be at least 12 years old");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
