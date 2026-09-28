package com.gymflow.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AccountValidationTest {
    @Test
    void normalizesEmailAndRejectsMissingAddressParts() {
        assertEquals("owner@example.com", AccountValidation.normalizeEmail("  OWNER@Example.COM  "));
        for (String email : new String[] {null, "", "@example.com", "owner@", "a@@b"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AccountValidation.normalizeEmail(email));
        }
    }

    @Test
    void normalizesSupportedSingaporeNumbersAndRejectsOthers() {
        assertEquals("+65 8123 4567", AccountValidation.normalizePhone("+65 (8123) 4567"));
        assertEquals("+65 3123 4567", AccountValidation.normalizePhone("3123-4567"));
        for (String phone : new String[] {null, "", "71234567", "8123456", "812345678",
                "+66 81234567", "8123.4567"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AccountValidation.normalizePhone(phone));
        }
    }

    @Test
    void passwordLengthIncludesBothLimits() {
        AccountValidation.validatePassword(new char[12]);
        AccountValidation.validatePassword(new char[128]);
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.validatePassword(null));
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.validatePassword(new char[11]));
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.validatePassword(new char[129]));
    }
}
