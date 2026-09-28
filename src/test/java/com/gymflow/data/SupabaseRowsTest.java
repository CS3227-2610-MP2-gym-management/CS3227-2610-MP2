package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SupabaseRowsTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsMissingPaymentReferenceToAnEmptyString() throws Exception {
        var row = json.readTree("""
                {
                  "id": 4,
                  "membership_id": 3,
                  "amount_cents": 12000,
                  "method": "CARD",
                  "paid_at": "2026-09-28T10:00:00Z",
                  "reference": null,
                  "recorded_by_account_id": 1,
                  "created_at": "2026-09-28T10:00:00Z"
                }
                """);

        assertEquals("", SupabaseRows.payment(row).reference());
    }
}
