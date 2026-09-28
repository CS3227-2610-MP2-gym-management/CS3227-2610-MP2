package com.gymflow.auth;

/** Supplies a current user access token for authenticated backend requests. */
@FunctionalInterface
public interface AccessTokenProvider {
    /** Returns a valid access token or fails when no user is signed in. */
    String requireAccessToken();
}
