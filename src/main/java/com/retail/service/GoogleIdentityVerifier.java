package com.retail.service;

/** Verifies a Google ID token and returns only claims trusted after signature/audience validation. */
public interface GoogleIdentityVerifier {
    VerifiedGoogleIdentity verify(String idToken);

    record VerifiedGoogleIdentity(String subject, String email, String name, boolean emailVerified) { }

    class InvalidGoogleTokenException extends RuntimeException {
        public InvalidGoogleTokenException() {
            super("Google ID token is invalid");
        }
    }

    class GoogleVerificationUnavailableException extends RuntimeException {
        public GoogleVerificationUnavailableException() {
            super("Google sign-in is not configured");
        }
    }
}
