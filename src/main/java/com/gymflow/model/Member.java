package com.gymflow.model;

import java.time.LocalDate;

/** Owner-visible Member account and profile information. */
public record Member(long accountId, String memberNumber, String email,
        String fullName, String phoneNumber, LocalDate dateOfBirth) {
}
