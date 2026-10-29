package com.retail.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;


@Component
public class GoogleIdentityTokenVerifier implements GoogleIdentityVerifier {
    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleIdentityTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(clientId.trim()))
                    .build();
        }
    }

    GoogleIdentityTokenVerifier(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public GoogleIdentityVerifier.VerifiedGoogleIdentity verify(String idToken) {
        if (verifier == null) throw new GoogleIdentityVerifier.GoogleVerificationUnavailableException();
        if (idToken == null || idToken.isBlank() || idToken.length() > 8192) {
            throw new GoogleIdentityVerifier.InvalidGoogleTokenException();
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) throw new GoogleIdentityVerifier.InvalidGoogleTokenException();
            GoogleIdToken.Payload payload = token.getPayload();
            String subject = payload.getSubject();
            String email = payload.getEmail();
            Object verifiedClaim = payload.get("email_verified");
            String name = payload.get("name") instanceof String value ? value : "";
            if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
                throw new GoogleIdentityVerifier.InvalidGoogleTokenException();
            }
            return new GoogleIdentityVerifier.VerifiedGoogleIdentity(
                    subject, email, name, Boolean.TRUE.equals(verifiedClaim));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new GoogleIdentityVerifier.InvalidGoogleTokenException();
        }
    }
}
