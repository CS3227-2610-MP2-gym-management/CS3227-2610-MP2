package com.gymflow.model;

/** Owner-visible Visit with Member identity fields. */
public record VisitOverview(Visit visit, String memberNumber,
        String memberName, String memberEmail) {
}
