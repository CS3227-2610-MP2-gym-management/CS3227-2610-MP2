package com.gymflow.visit;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.MemberVisitStore;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.data.SupabaseVisitStore;
import com.gymflow.model.Account;
import com.gymflow.model.MemberVisitState;
import com.gymflow.model.Role;
import com.gymflow.model.Visit;

/** Authorizes Member self-service check-in, check-out, and current Visit state. */
public final class MemberVisitService {
    private final MemberVisitStore visits;
    private final SupabaseVisitStore cloudVisits;
    private final Clock clock;

    /** Creates a service using the system clock. */
    public MemberVisitService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock. */
    public MemberVisitService(GymFlowDatabase database, Clock clock) {
        visits = new MemberVisitStore(Objects.requireNonNull(database));
        cloudVisits = null;
        this.clock = Objects.requireNonNull(clock);
    }

    /** Creates a Supabase-backed service using the system clock. */
    public MemberVisitService(SupabaseDataClient client) {
        visits = null;
        cloudVisits = new SupabaseVisitStore(Objects.requireNonNull(client));
        clock = Clock.systemDefaultZone();
    }

    /** Returns whether this active Member currently has an open Visit. */
    public MemberVisitState currentState(Account actor) {
        requireMember(actor);
        return cloudVisits == null
                ? visits.currentState(actor.id()) : cloudVisits.currentState(actor.id());
    }

    /** Opens a Visit for this active Member when a valid Membership covers today. */
    public Visit checkIn(Account actor) {
        requireMember(actor);
        return cloudVisits == null
                ? visits.checkIn(actor.id(), LocalDate.now(clock), clock.instant())
                : cloudVisits.checkIn(actor.id(), LocalDate.now(clock), clock.instant());
    }

    /** Closes this active Member's open Visit. */
    public Visit checkOut(Account actor) {
        requireMember(actor);
        return cloudVisits == null
                ? visits.checkOut(actor.id(), clock.instant())
                : cloudVisits.checkOut(actor.id(), clock.instant());
    }

    private static void requireMember(Account actor) {
        if (actor == null || actor.role() != Role.MEMBER || !actor.active()) {
            throw new IllegalArgumentException("An active Member account is required");
        }
    }
}
