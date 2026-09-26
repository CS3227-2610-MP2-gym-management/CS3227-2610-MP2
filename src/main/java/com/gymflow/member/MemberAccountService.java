package com.gymflow.member;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;

import com.gymflow.auth.AccountValidation;
import com.gymflow.auth.PasswordHasher;
import com.gymflow.data.AccountStore;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.MemberAccountStore;
import com.gymflow.model.Account;
import com.gymflow.model.MemberOverview;
import com.gymflow.model.MembershipNotice;
import com.gymflow.model.MembershipNoticeState;
import com.gymflow.model.MembershipStatus;
import com.gymflow.model.Role;

/** Authorizes and loads account data used by Member-facing screens. */
public final class MemberAccountService {
    private final MemberAccountStore accounts;
    private final AccountStore accountStore;
    private final PasswordHasher passwords = new PasswordHasher();
    private final Clock clock;

    /** Creates a service using the system clock. */
    public MemberAccountService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock for deterministic status derivation. */
    public MemberAccountService(GymFlowDatabase database, Clock clock) {
        accounts = new MemberAccountStore(Objects.requireNonNull(database));
        accountStore = new AccountStore(database);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Loads the authenticated Member's profile and ordered Membership history. */
    public MemberOverview loadOverview(Account actor) {
        requireMember(actor);
        return new MemberOverview(accounts.profile(actor.id()), accounts.membershipHistory(actor.id()));
    }

    /** Updates the authenticated Member's self-service contact details. */
    public com.gymflow.model.Member updateContact(Account actor, String email, String phoneNumber) {
        requireMember(actor);
        return accounts.updateContact(actor.id(), AccountValidation.normalizeEmail(email),
                AccountValidation.normalizePhone(phoneNumber));
    }

    /** Replaces the authenticated Member's password after verifying the current password. */
    public void changePassword(Account actor, char[] currentPassword, char[] newPassword) {
        try {
            requireMember(actor);
            AccountValidation.validatePassword(newPassword);
            var stored = accountStore.findById(actor.id())
                    .filter(item -> item.account().role() == Role.MEMBER && item.account().active())
                    .orElseThrow(() -> new IllegalArgumentException("An active Member account is required"));
            if (!passwords.verify(currentPassword, stored.password())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            accountStore.updateMemberPassword(actor.id(), passwords.hash(newPassword));
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
}
