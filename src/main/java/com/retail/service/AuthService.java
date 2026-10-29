package com.retail.service;

import com.retail.dto.AuthResponse;
import com.retail.dto.CreateReceptionistRequest;
import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Set<String> WORKSPACE_THEMES = Set.of("forest", "ocean", "berry", "clay");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final JdbcTemplate jdbcTemplate;

    public AuthResponse login(String email, String password) {
        AppUser user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(candidate -> candidate.isActive())
                .filter(candidate -> candidate.getPasswordHash() != null
                        && passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return AuthResponse.from(jwtService.createToken(user), jwtService.getExpirationSeconds(), user);
    }

    @Transactional
    public AuthResponse googleLogin(String idToken) {
        GoogleIdentityVerifier.VerifiedGoogleIdentity identity = googleIdentityVerifier.verify(idToken);
        if (!identity.emailVerified()) throw new GoogleIdentityVerifier.InvalidGoogleTokenException();

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

    public AuthResponse.UserInfo createReceptionist(CreateReceptionistRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = new AppUser();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setRole(UserRole.RECEPTIONIST);
        user.setActive(true);
        try {
            return AuthResponse.UserInfo.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists", exception);
        }
    }

    public AuthResponse.UserInfo currentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email).filter(candidate -> candidate.isActive())
                .map(AuthResponse.UserInfo::from)
                .orElseThrow(InvalidCredentialsException::new);
    }

    public AuthResponse.UserInfo updateWorkspaceTheme(String email, String workspaceTheme) {
        String theme = workspaceTheme.trim().toLowerCase(Locale.ROOT);
        if (!WORKSPACE_THEMES.contains(theme)) {
            throw new IllegalArgumentException("Unsupported workspace theme");
        }
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);
        user.setWorkspaceTheme(theme);
        return AuthResponse.UserInfo.from(userRepository.save(user));
    }

    public List<AuthResponse.UserInfo> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(AuthResponse.UserInfo::from)
                .toList();
    }

    public Page<AuthResponse.UserInfo> listUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.findAll(pageable).map(AuthResponse.UserInfo::from);
    }

    @Transactional
    public void bootstrapInitialOwner(String name, String email) {
        if (email == null || email.isBlank()) return;
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(624118730215)");
        if (userRepository.existsByRole(UserRole.OWNER)) return;
        String normalizedEmail = normalizeEmail(email);
        AppUser existing = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existing != null) {
            throw new IllegalStateException("Configured bootstrap email already belongs to a non-owner account");
        }
        AppUser owner = new AppUser();
        owner.setName(name == null || name.isBlank() ? "Owner" : name.trim());
        owner.setEmail(normalizedEmail);
        owner.setRole(UserRole.OWNER);
        owner.setActive(true);
        userRepository.saveAndFlush(owner);
    }

    @Transactional
    public void logout(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(email).filter(candidate -> candidate.isActive())
                .orElseThrow(InvalidCredentialsException::new);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse.UserInfo updateUserStatus(String actorEmail, UUID userId, boolean active) {
        AppUser actor = activeUser(actorEmail);
        AppUser target = userById(userId);
        preventSelfLockout(actor, target);
        if (!active && target.isActive() && target.getRole() == UserRole.OWNER
                && userRepository.countByRoleAndActiveTrue(UserRole.OWNER) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active owner cannot be deactivated");
        }
        if (target.isActive() != active) target.setTokenVersion(target.getTokenVersion() + 1);
        target.setActive(active);
        return AuthResponse.UserInfo.from(userRepository.save(target));
    }

    @Transactional
    public AuthResponse.UserInfo updateUserRole(String actorEmail, UUID userId, UserRole role) {
        AppUser actor = activeUser(actorEmail);
        AppUser target = userById(userId);
        preventSelfLockout(actor, target);
        if (target.isActive() && target.getRole() == UserRole.OWNER && role != UserRole.OWNER
                && userRepository.countByRoleAndActiveTrue(UserRole.OWNER) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active owner cannot be demoted");
        }
        if (target.getRole() != role) target.setTokenVersion(target.getTokenVersion() + 1);
        target.setRole(role);
        return AuthResponse.UserInfo.from(userRepository.save(target));
    }

    private AppUser activeUser(String email) {
        return userRepository.findByEmailIgnoreCase(email).filter(candidate -> candidate.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authentication is required"));
    }

    private AppUser userById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found"));
    }

    private void preventSelfLockout(AppUser actor, AppUser target) {
        if (actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own account access");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }
}
