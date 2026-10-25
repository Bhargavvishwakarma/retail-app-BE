package com.retail.service;

import com.retail.dto.AuthResponse;
import com.retail.dto.CreateReceptionistRequest;
import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Set<String> WORKSPACE_THEMES = Set.of("forest", "ocean", "berry", "clay");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse login(String email, String password) {
        AppUser user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return AuthResponse.from(jwtService.createToken(user), jwtService.getExpirationSeconds(), user);
    }

    public AuthResponse.UserInfo createReceptionist(CreateReceptionistRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        AppUser user = new AppUser();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.RECEPTIONIST);
        return AuthResponse.UserInfo.from(userRepository.save(user));
    }

    public AuthResponse.UserInfo currentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
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

    public void createInitialOwner(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) return;
        AppUser owner = new AppUser();
        owner.setName(name == null || name.isBlank() ? "Owner" : name.trim());
        owner.setEmail(normalizedEmail);
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setRole(UserRole.OWNER);
        userRepository.save(owner);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }
}
