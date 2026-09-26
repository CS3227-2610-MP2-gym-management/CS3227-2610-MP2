package com.gymflow.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BodyMetricServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
    @TempDir Path directory;
    private Account member;
    private BodyMetricService metrics;

    @BeforeEach
    void setUp() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        AuthenticationService authentication = new AuthenticationService(database);
        Account owner = authentication.createOwner("owner@example.com", "owner password".toCharArray());
        new OwnerMemberService(database).createMember(new CreateMemberRequest("member@example.com",
                "member password".toCharArray(), "Member Tan", "81234567", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), new BigDecimal("50"),
                PaymentMethod.CARD, NOW, ""), owner.id());
        member = authentication.authenticate("member@example.com", "member password".toCharArray()).orElseThrow();
        metrics = new BodyMetricService(database, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesUpdatesAndOrdersPreciseKilogramReadings() {
        var old = metrics.create(member, LocalDate.of(2026, 9, 14), new BigDecimal("70.125"));
        var current = metrics.create(member, LocalDate.of(2026, 9, 15), new BigDecimal("69.9"));
        var updated = metrics.update(member, old.id(), LocalDate.of(2026, 9, 13), new BigDecimal("70.001"));

        assertEquals(new BigDecimal("70.001"), updated.weightKilograms());
        assertEquals(current.id(), metrics.history(member).getFirst().id());
    }

    @Test
    void rejectsInvalidDatesWeightsAndDuplicateDates() {
        metrics.create(member, LocalDate.of(2026, 9, 15), new BigDecimal("70"));

        assertThrows(IllegalArgumentException.class,
                () -> metrics.create(member, LocalDate.of(2026, 9, 16), new BigDecimal("70")));
        assertThrows(IllegalArgumentException.class,
                () -> metrics.create(member, LocalDate.of(2026, 9, 14), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> metrics.create(member, LocalDate.of(2026, 9, 14), new BigDecimal("70.0001")));
        assertThrows(IllegalStateException.class,
                () -> metrics.create(member, LocalDate.of(2026, 9, 15), new BigDecimal("71")));
    }
}
