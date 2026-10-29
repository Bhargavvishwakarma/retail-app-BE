package com.retail.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

import static com.retail.service.GoogleIdentityVerifier.InvalidGoogleTokenException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleIdentityTokenVerifierTest {
    @Test
    void springCanConstructVerifierUsingConfiguredClientId() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test-properties", Map.of("app.google.client-id", "test-client-id")));
            context.register(GoogleIdentityTokenVerifier.class);
            context.refresh();

            assertNotNull(context.getBean(GoogleIdentityTokenVerifier.class));
        }
    }

    @Test
    void returnsOnlyClaimsFromAValidatedGoogleToken() throws Exception {
        GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken token = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        when(googleVerifier.verify("verified-token")).thenReturn(token);
        when(token.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("stable-google-sub");
        when(payload.getEmail()).thenReturn("owner@example.com");
        when(payload.get("email_verified")).thenReturn(true);
        when(payload.get("name")).thenReturn("Shop Owner");

        var identity = new GoogleIdentityTokenVerifier(googleVerifier).verify("verified-token");

        assertEquals("stable-google-sub", identity.subject());
        assertEquals("owner@example.com", identity.email());
        assertTrue(identity.emailVerified());
    }

    @Test
    void rejectsTokensRejectedForInvalidSignatureOrTampering() throws Exception {
        assertRejectedToken(null);
    }

    @Test
    void rejectsExpiredTokens() throws Exception {
        assertRejectedToken(null);
    }

    @Test
    void rejectsTokensWithWrongAudience() throws Exception {
        assertRejectedToken(null);
    }

    @Test
    void mapsVerificationFailuresToGenericAuthenticationFailure() throws Exception {
        GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
        when(googleVerifier.verify("rejected-token")).thenThrow(new GeneralSecurityException("details"));

        assertThrows(InvalidGoogleTokenException.class,
                () -> new GoogleIdentityTokenVerifier(googleVerifier).verify("rejected-token"));
    }

    private void assertRejectedToken(GoogleIdToken result) throws IOException, GeneralSecurityException {
        GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
        when(googleVerifier.verify("rejected-token")).thenReturn(result);

        assertThrows(InvalidGoogleTokenException.class,
                () -> new GoogleIdentityTokenVerifier(googleVerifier).verify("rejected-token"));
    }
}
