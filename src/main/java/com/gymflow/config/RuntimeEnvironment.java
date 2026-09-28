package com.gymflow.config;

/** Identifies the backend environment selected for this application process. */
public enum RuntimeEnvironment {
    LOCAL,
    PRODUCTION;

    static RuntimeEnvironment parse(String value) {
        try {
            return value == null || value.isBlank()
                    ? LOCAL
                    : valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("GYMFLOW_ENV must be local or production", exception);
        }
    }
}
