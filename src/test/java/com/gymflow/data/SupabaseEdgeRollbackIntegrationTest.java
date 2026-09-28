package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.gymflow.auth.SupabaseAuthenticationService;
import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.PaymentMethod;

@EnabledIfEnvironmentVariable(named = "GYMFLOW_LOCAL_INTEGRATION", matches = "true")
class SupabaseEdgeRollbackIntegrationTest {
    private static final String LOCAL_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

    @Test
    void failedMemberRecordCreationRemovesAuthUserAndFailedUpdateRestoresLoginEmail() {
        SupabaseConfiguration configuration = new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                URI.create("http://127.0.0.1:54321"), LOCAL_KEY);
        SupabaseAuthenticationService authentication = new SupabaseAuthenticationService(configuration);
        SupabaseDataClient client = new SupabaseDataClient(configuration, authentication);
        OwnerMemberService members = new OwnerMemberService(client);
        var owner = authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();
        String prefix = UUID.randomUUID().toString();
        String originalEmail = prefix + "@example.test";
        String changedEmail = "changed-" + originalEmail;
        String password = "RollbackMember!2026";

        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("action", "create");
        invalid.put("email", originalEmail);
        invalid.put("password", password);
        invalid.put("full_name", "Rollback Member");
        invalid.put("phone_number", "+65 8000 0098");
        invalid.put("date_of_birth", null);
        invalid.put("membership_start", "2030-02-02");
        invalid.put("membership_expiry", "2030-02-01");
        invalid.put("payment_amount_cents", 5000);
        invalid.put("payment_method", "CARD");
        invalid.put("paid_at", Instant.now().toString());
        invalid.put("payment_reference", null);
        assertThrows(IllegalStateException.class, () -> client.function("manage-member", invalid));
        assertTrue(authentication.authenticate(originalEmail, password.toCharArray()).isEmpty());

        authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();
        var member = members.createMember(new CreateMemberRequest(originalEmail,
                password.toCharArray(), "Rollback Member", "+65 8000 0098", null,
                LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("50.00"),
                PaymentMethod.CARD, Instant.now(), null), owner.id());
        assertEquals(originalEmail, member.email());

        Map<String, Object> badUpdate = new LinkedHashMap<>();
        badUpdate.put("action", "update");
        badUpdate.put("member_account_id", member.accountId());
        badUpdate.put("email", changedEmail);
        badUpdate.put("phone_number", "+65 8000 0098");
        badUpdate.put("full_name", " ");
        assertThrows(IllegalStateException.class, () -> client.function("manage-member", badUpdate));
        assertEquals(member.accountId(), authentication.authenticate(originalEmail,
                password.toCharArray()).orElseThrow().id());
        assertTrue(authentication.authenticate(changedEmail, password.toCharArray()).isEmpty());
    }
}
