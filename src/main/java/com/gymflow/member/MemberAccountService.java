package com.gymflow.member;

import java.time.Clock;
import java.util.Objects;

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
    private final Clock clock;

    /** Creates a service using the system clock. */
    public MemberAccountService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock for deterministic status derivation. */
    public MemberAccountService(GymFlowDatabase database, Clock clock) {
        accounts = new MemberAccountStore(Objects.requireNonNull(database));
        this.clock = Objects.requireNonNull(clock);
    }

    /** Loads the authenticated Member's profile and ordered Membership history. */
    public MemberOverview loadOverview(Account actor) {
        requireMember(actor);
        return new MemberOverview(accounts.profile(actor.id()), accounts.membershipHistory(actor.id()));
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
}
