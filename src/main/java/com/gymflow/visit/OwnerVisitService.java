package com.gymflow.visit;

import java.util.List;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.OwnerVisitStore;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;

/** Provides read-only Visit information for Owner screens. */
public final class OwnerVisitService {
    private final OwnerVisitStore visits;

    /** Creates an Owner Visit service backed by the supplied database. */
    public OwnerVisitService(GymFlowDatabase database) {
        visits = new OwnerVisitStore(database);
    }

    /** Searches all Visits or only Visits without an exit time. */
    public List<VisitOverview> searchVisits(String query, boolean currentlyVisitingOnly) {
        return visits.search(query == null ? "" : query.trim(), currentlyVisitingOnly);
    }

    /** Lists one Member's Visit history. */
    public List<Visit> visitHistory(long memberId) {
        return visits.history(memberId);
    }

    /** Returns the number of Members currently inside the gym. */
    public long currentVisitorCount() {
        return visits.countCurrentlyVisiting();
    }
}
