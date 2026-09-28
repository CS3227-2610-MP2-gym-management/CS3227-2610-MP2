package com.gymflow.member;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Objects;

import com.gymflow.auth.AccountValidation;
import com.gymflow.auth.PasswordHasher;
import com.gymflow.auth.SupabaseAuthenticationService;
import com.gymflow.data.AccountStore;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.MemberAccountStore;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.data.SupabaseMemberAccountStore;
import com.gymflow.model.Account;
import com.gymflow.model.MemberOverview;
import com.gymflow.model.MembershipNotice;
import com.gymflow.model.MembershipNoticeState;
import com.gymflow.model.MembershipStatus;
import com.gymflow.model.Role;

/** Authorizes and loads account data used by Member-facing screens. */
public final class MemberAccountService {
    private final MemberAccountStore accounts;
    private final SupabaseMemberAccountStore cloudAccounts;
    private final AccountStore accountStore;
    private final SupabaseAuthenticationService cloudAuthentication;
    private final PasswordHasher passwords = new PasswordHasher();
    private final Clock clock;

    /** Creates a service using the system clock. */
    public MemberAccountService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock for deterministic status derivation. */
    public MemberAccountService(GymFlowDatabase database, Clock clock) {
        accounts = new MemberAccountStore(Objects.requireNonNull(database));
        cloudAccounts = null;
        accountStore = new AccountStore(database);
        cloudAuthentication = null;
        this.clock = Objects.requireNonNull(clock);
    }

    /** Creates a Supabase-backed service using the system clock. */
    public MemberAccountService(SupabaseDataClient client,
            SupabaseAuthenticationService authentication) {
        accounts = null;
        cloudAccounts = new SupabaseMemberAccountStore(Objects.requireNonNull(client));
        accountStore = null;
        cloudAuthentication = Objects.requireNonNull(authentication);
        clock = Clock.system(ZoneId.of("Asia/Singapore"));
    }

    /** Loads the authenticated Member's profile and ordered Membership history. */
    public MemberOverview loadOverview(Account actor) {
        requireMember(actor);
        return cloudAccounts == null
                ? new MemberOverview(accounts.profile(actor.id()), accounts.membershipHistory(actor.id()))
                : new MemberOverview(cloudAccounts.profile(actor.id()),
                        cloudAccounts.membershipHistory(actor.id()));
    }

    /** Updates the authenticated Member's self-service contact details. */
    public com.gymflow.model.Member updateContact(Account actor, String email, String phoneNumber,
            char[] currentPassword) {
        try {
            requireMember(actor);
            String normalizedEmail = AccountValidation.normalizeEmail(email);
            String normalizedPhone = AccountValidation.normalizePhone(phoneNumber);
            if (cloudAccounts == null) {
                var stored = accountStore.findById(actor.id())
                        .filter(item -> item.account().role() == Role.MEMBER && item.account().active())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "An active Member account is required"));
                if (!passwords.verify(currentPassword, stored.password())) {
                    throw new IllegalArgumentException("Current password is incorrect");
                }
                return accounts.updateContact(actor.id(), normalizedEmail, normalizedPhone);
            }
            if (cloudAuthentication.authenticate(actor.email(), copy(currentPassword)).isEmpty()) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            cloudAccounts.updateContact(actor.id(), normalizedEmail, normalizedPhone);
            if (cloudAuthentication.authenticate(normalizedEmail, copy(currentPassword)).isEmpty()) {
                throw new IllegalStateException("Sign in again after changing your email address");
            }
            return cloudAccounts.profile(actor.id());
        } finally {
            clear(currentPassword);
        }
    }

    /** Replaces the authenticated Member's password after verifying the current password. */
    public void changePassword(Account actor, char[] currentPassword, char[] newPassword) {
        try {
            requireMember(actor);
            AccountValidation.validatePassword(newPassword);
            if (cloudAuthentication == null) {
                var stored = accountStore.findById(actor.id())
                        .filter(item -> item.account().role() == Role.MEMBER && item.account().active())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "An active Member account is required"));
                if (!passwords.verify(currentPassword, stored.password())) {
                    throw new IllegalArgumentException("Current password is incorrect");
                }
                accountStore.updateMemberPassword(actor.id(), passwords.hash(newPassword));
            } else {
                cloudAuthentication.changePassword(currentPassword, newPassword);
            }
        } finally {
            clear(currentPassword);
            clear(newPassword);
        }
    }

    /** Returns the local date used to derive Membership display statuses. */
    public java.time.LocalDate today() {
        return java.time.LocalDate.now(clock);
    }

    /** Derives the one Membership state that Member screens should emphasize. */
    public MembershipNotice membershipNotice(MemberOverview overview) {
        Objects.requireNonNull(overview);
        return overview.memberships().stream()
                .filter(item -> item.status(today()) == MembershipStatus.ACTIVE)
                .findFirst()
                .map(item -> new MembershipNotice(MembershipNoticeState.ACTIVE, item))
                .orElseGet(() -> overview.memberships().stream()
                        .filter(item -> item.status(today()) == MembershipStatus.UPCOMING)
                        .findFirst()
                        .map(item -> new MembershipNotice(MembershipNoticeState.UPCOMING, item))
                        .orElseGet(() -> new MembershipNotice(MembershipNoticeState.RENEWAL_NEEDED, null)));
    }

    /** Returns whether the authenticated Member has access today. */
    public boolean hasCurrentMembership(Account actor) {
        return membershipNotice(loadOverview(actor)).state() == MembershipNoticeState.ACTIVE;
    }

    private static void requireMember(Account actor) {
        if (actor == null || actor.role() != Role.MEMBER || !actor.active()) {
            throw new IllegalArgumentException("An active Member account is required");
        }
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private static char[] copy(char[] password) {
        return password == null ? null : Arrays.copyOf(password, password.length);
    }
}
