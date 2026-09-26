package com.gymflow.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One Member-owned body-mass reading. */
public record BodyMetric(long id, long memberAccountId, LocalDate measurementDate,
        BigDecimal weightKilograms, Instant createdAt, Instant updatedAt) {
}
