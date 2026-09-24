package com.gymflow.model;

/** Derived current Visit state for an authenticated Member. */
public record MemberVisitState(Visit openVisit) {
    /** Returns whether the Member currently has an open Visit. */
    public boolean checkedIn() {
        return openVisit != null;
    }
}
