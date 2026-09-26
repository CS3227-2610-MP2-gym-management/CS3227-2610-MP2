package com.gymflow.metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.gymflow.data.BodyMetricStore;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import com.gymflow.model.BodyMetric;
import com.gymflow.model.Role;

/** Validates and authorizes Member body-mass commands. */
public final class BodyMetricService {
    private final BodyMetricStore store;
    private final Clock clock;

    /** Creates a service using the system clock. */
    public BodyMetricService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock. */
    public BodyMetricService(GymFlowDatabase database, Clock clock) {
        store = new BodyMetricStore(Objects.requireNonNull(database));
        this.clock = Objects.requireNonNull(clock);
    }

    /** Lists the authenticated Member's readings. */
    public List<BodyMetric> history(Account actor) {
        requireMember(actor);
        return store.findByMember(actor.id());
    }

    /** Records a new body-mass reading. */
    public BodyMetric create(Account actor, LocalDate date, BigDecimal kilograms) {
        requireMember(actor);
        return store.create(actor.id(), validateDate(date), grams(kilograms), clock.instant());
    }

    /** Updates a body-mass reading. */
    public BodyMetric update(Account actor, long id, LocalDate date, BigDecimal kilograms) {
        requireMember(actor);
        return store.update(actor.id(), id, validateDate(date), grams(kilograms), clock.instant());
    }

    /** Deletes a body-mass reading. */
    public void delete(Account actor, long id) {
        requireMember(actor);
        store.delete(actor.id(), id);
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
}
