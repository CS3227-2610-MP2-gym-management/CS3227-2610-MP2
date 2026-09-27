package com.gymflow.visit;

import java.time.Instant;
import java.util.List;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.OwnerVisitStore;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.data.SupabaseVisitStore;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;

/** Provides read-only Visit information for Owner screens. */
public final class OwnerVisitService {
    private final OwnerVisitStore visits;
    private final SupabaseVisitStore cloudVisits;

    /** Creates an Owner Visit service backed by the supplied database. */
    public OwnerVisitService(GymFlowDatabase database) {
        visits = new OwnerVisitStore(database);
        cloudVisits = null;
    }

    /** Creates an Owner Visit service backed by Supabase. */
    public OwnerVisitService(SupabaseDataClient client) {
        visits = null;
        cloudVisits = new SupabaseVisitStore(client);
    }

    /** Searches all Visits or only Visits without an exit time. */
    public List<VisitOverview> searchVisits(String query, boolean currentlyVisitingOnly) {
        String normalized = query == null ? "" : query.trim();
        return cloudVisits == null
                ? visits.search(normalized, currentlyVisitingOnly)
                : cloudVisits.search(normalized, currentlyVisitingOnly);
    }

    /** Lists one Member's Visit history. */
    public List<Visit> visitHistory(long memberId) {
        return cloudVisits == null ? visits.history(memberId) : cloudVisits.history(memberId);
    }

    /** Returns the number of Members currently inside the gym. */
    public long currentVisitorCount() {
        return cloudVisits == null
                ? visits.countCurrentlyVisiting() : cloudVisits.countCurrentlyVisiting();
    }

    /** Corrects Visit timestamps and records the responsible Owner and reason. */
    public Visit correctVisit(long visitId, Instant enteredAt, Instant exitedAt,
            String correctionReason, long ownerAccountId) {
        if (enteredAt == null) {
            throw new IllegalArgumentException("Entry time is required");
        }
        if (exitedAt != null && !enteredAt.isBefore(exitedAt)) {
            throw new IllegalArgumentException("Exit time must be after entry time");
        }
        String reason = correctionReason == null ? "" : correctionReason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Correction reason is required");
        }
        return cloudVisits == null
                ? visits.correct(visitId, enteredAt, exitedAt, reason, ownerAccountId)
                : cloudVisits.correct(visitId, enteredAt, exitedAt, reason, ownerAccountId);
    }
}
