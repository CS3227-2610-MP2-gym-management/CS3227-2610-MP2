package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SupabaseDataClientTest {
    @Test
    void exposesOnlyTheKnownDuplicateEmailValidationError() {
        RuntimeException duplicate = SupabaseDataClient.responseFailure(400,
                "{\"code\":\"duplicate_email\","
                        + "\"message\":\"An account with this email already exists\"}",
                "complete the GymFlow account operation", new ObjectMapper());
        RuntimeException unknown = SupabaseDataClient.responseFailure(400,
                "{\"message\":\"database internals\"}",
                "complete the GymFlow account operation", new ObjectMapper());

        assertInstanceOf(IllegalArgumentException.class, duplicate);
        assertEquals("An account with this email already exists", duplicate.getMessage());
        assertInstanceOf(IllegalStateException.class, unknown);
        assertEquals("Unable to complete the GymFlow account operation (HTTP 400: database internals)",
                unknown.getMessage());
    }
}
