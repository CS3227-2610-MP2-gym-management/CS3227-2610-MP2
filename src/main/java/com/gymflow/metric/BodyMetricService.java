package com.gymflow.metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.gymflow.data.BodyMetricStore;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.MemberAccountStore;
import com.gymflow.data.SupabaseBodyMetricStore;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.data.SupabaseMemberAccountStore;
import com.gymflow.model.Account;
import com.gymflow.model.BodyMetric;
import com.gymflow.model.MembershipStatus;
import com.gymflow.model.Role;

/** Validates and authorizes Member body-mass commands. */
public final class BodyMetricService {
    private final BodyMetricStore store;
    private final SupabaseBodyMetricStore cloudStore;
    private final Clock clock;
    private final MemberAccountStore memberAccounts;
    private final SupabaseMemberAccountStore cloudMemberAccounts;

    /** Creates a service using the system clock. */
    public BodyMetricService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock. */
    public BodyMetricService(GymFlowDatabase database, Clock clock) {
        store = new BodyMetricStore(Objects.requireNonNull(database));
        cloudStore = null;
        memberAccounts = new MemberAccountStore(database);
        cloudMemberAccounts = null;
        this.clock = Objects.requireNonNull(clock);
    }

    /** Creates a Supabase-backed service using the system clock. */
    public BodyMetricService(SupabaseDataClient client) {
        store = null;
        cloudStore = new SupabaseBodyMetricStore(Objects.requireNonNull(client));
        memberAccounts = null;
        cloudMemberAccounts = new SupabaseMemberAccountStore(client);
        clock = Clock.systemDefaultZone();
    }

    /** Lists the authenticated Member's readings. */
    public List<BodyMetric> history(Account actor) {
        requireMember(actor);
        return cloudStore == null ? store.findByMember(actor.id()) : cloudStore.findByMember(actor.id());
    }

    /** Records a new body-mass reading. */
    public BodyMetric create(Account actor, LocalDate date, BigDecimal kilograms) {
        requireMember(actor);
        requireCurrentMembership(actor);
        LocalDate validated = validateDate(date);
        return cloudStore == null
                ? store.create(actor.id(), validated, grams(kilograms), clock.instant())
                : cloudStore.create(actor.id(), validated, grams(kilograms), clock.instant());
    }

    /** Updates a body-mass reading. */
    public BodyMetric update(Account actor, long id, LocalDate date, BigDecimal kilograms) {
        requireMember(actor);
        LocalDate validated = validateDate(date);
        return cloudStore == null
                ? store.update(actor.id(), id, validated, grams(kilograms), clock.instant())
                : cloudStore.update(actor.id(), id, validated, grams(kilograms), clock.instant());
    }

    /** Deletes a body-mass reading. */
    public void delete(Account actor, long id) {
        requireMember(actor);
        if (cloudStore == null) {
            store.delete(actor.id(), id);
        } else {
            cloudStore.delete(actor.id(), id);
        }
    }

    private LocalDate validateDate(LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Measurement date cannot be in the future");
        }
        return date;
    }

    private static long grams(BigDecimal kilograms) {
        if (kilograms == null || kilograms.signum() <= 0 || kilograms.scale() > 3) {
            throw new IllegalArgumentException("Body mass must be positive with at most three decimal places");
        }
        try {
            return kilograms.movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Body mass is invalid", exception);
        }
    }

    private static void requireMember(Account actor) {
        if (actor == null || actor.role() != Role.MEMBER || !actor.active()) {
            throw new IllegalArgumentException("An active Member account is required");
        }
    }

    private void requireCurrentMembership(Account actor) {
        boolean current = (cloudMemberAccounts == null
                ? memberAccounts.membershipHistory(actor.id())
                : cloudMemberAccounts.membershipHistory(actor.id())).stream()
                .anyMatch(item -> item.status(LocalDate.now(clock)) == MembershipStatus.ACTIVE);
        if (!current) {
            throw new IllegalArgumentException("A current Membership is required to record body mass");
        }
    }
}
