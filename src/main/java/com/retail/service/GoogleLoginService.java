package com.retail.service;

import com.retail.dto.AuthResponse;
import com.retail.model.AppUser;
import com.retail.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Performs only the database portion of a Google sign-in.
 *
 * <p>Google token verification deliberately happens before this service is
 * called.  Keeping the remote Google request outside this transaction means a
 * slow or unavailable Google endpoint cannot consume a JDBC connection.</p>
 */
@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private final AppUserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse complete(GoogleIdentityVerifier.VerifiedGoogleIdentity identity) {
        String email = normalizeEmail(identity.email());
        AppUser user = userRepository.findByGoogleSub(identity.subject()).orElse(null);
        if (user != null) {
            if (!normalizeEmail(user.getEmail()).equals(email)) {
                throw new GoogleIdentityVerifier.InvalidGoogleTokenException();
            }
        } else {
            user = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "This Google account has not been invited"));
            if (user.getGoogleSub() != null && !user.getGoogleSub().equals(identity.subject())) {
                throw new GoogleIdentityVerifier.InvalidGoogleTokenException();
            }
            user.setGoogleSub(identity.subject());
        }

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is inactive");
        }
        if (user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is not authorized");
        }
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent first sign-in can race to claim this Google subject. The
            // unique database index makes one request win; the caller can retry.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Google identity could not be linked; retry sign-in", exception);
        }
        return AuthResponse.from(jwtService.createToken(user), jwtService.getExpirationSeconds(), user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
