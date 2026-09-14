package com.gymflow.member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.gymflow.auth.PasswordHash;
import com.gymflow.auth.PasswordHasher;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.OwnerMemberStore;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;

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
            String email = normalizeEmail(request.email());
            String phone = normalizePhone(request.phoneNumber());
            PasswordHash hash = passwords.hash(password);
            return members.create(request, email, phone, hash, ownerAccountId);
        } finally {
            clear(password);
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

    /** Updates editable account and profile fields. */
    public Member updateMember(long accountId, String email, String fullName,
            String phoneNumber, LocalDate dateOfBirth) {
        String normalizedEmail = normalizeEmail(email);
        requireText(fullName, "Full name is required");
        String normalizedPhone = normalizePhone(phoneNumber);
        validateDateOfBirth(dateOfBirth);
        return members.update(accountId, normalizedEmail, fullName.trim(), normalizedPhone, dateOfBirth);
    }

    private static void validate(CreateMemberRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Member details are required");
        }
        normalizeEmail(request.email());
        int passwordLength = request.initialPassword() == null ? 0 : request.initialPassword().length;
        if (passwordLength < 12 || passwordLength > 128) {
            throw new IllegalArgumentException("Password must be between 12 and 128 characters");
        }
        requireText(request.fullName(), "Full name is required");
        normalizePhone(request.phoneNumber());
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

    private static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return normalized;
    }

    private static String normalizePhone(String phoneNumber) {
        String compact = phoneNumber == null ? "" : phoneNumber.replaceAll("[\\s()-]", "");
        if (compact.startsWith("+65")) {
            compact = compact.substring(3);
        }
        if (!compact.matches("[3689]\\d{7}")) {
            throw new IllegalArgumentException("Enter a valid 8-digit Singapore phone number");
        }
        return "+65 %s %s".formatted(compact.substring(0, 4), compact.substring(4));
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
