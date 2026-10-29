package com.retail.service;

import com.retail.dto.AuthResponse;
import com.retail.dto.CreateReceptionistRequest;
import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.repository.AppUserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static com.retail.service.GoogleIdentityVerifier.InvalidGoogleTokenException;
import static com.retail.service.GoogleIdentityVerifier.VerifiedGoogleIdentity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceGoogleLoginTest {
    private AppUserRepository users;
    private GoogleIdentityVerifier googleVerifier;
    private JwtService jwtService;
    private AuthService authService;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        googleVerifier = mock(GoogleIdentityVerifier.class);
        jwtService = mock(JwtService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        authService = new AuthService(users, passwordEncoder, jwtService, googleVerifier, jdbcTemplate);
        owner = new AppUser();
        owner.setId(UUID.randomUUID());
        owner.setName("Shop Owner");
        owner.setEmail("owner@example.com");
        owner.setRole(UserRole.OWNER);
        owner.setActive(true);
        owner.setTokenVersion(0);
        when(jwtService.createToken(any(AppUser.class))).thenReturn("application-jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
    }

    @Test
    void verifiedOwnerSignsInAndGetsDatabaseRoleAndApplicationJwt() {
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("google-sub-1", owner.getEmail(), owner.getName(), true));
        when(users.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(owner));
        when(users.saveAndFlush(owner)).thenReturn(owner);

        AuthResponse response = authService.googleLogin("google-id-token");

        assertEquals("application-jwt", response.token());
        assertEquals(UserRole.OWNER, response.user().role());
        verify(users).saveAndFlush(owner);
    }

    @Test
    void verifiedReceptionistEmailCanBeLinkedOnlyToItsPreRegisteredAccount() {
        owner.setRole(UserRole.RECEPTIONIST);
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("google-sub-2", owner.getEmail(), owner.getName(), true));
        when(users.findByGoogleSub("google-sub-2")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.saveAndFlush(owner)).thenReturn(owner);

        AuthResponse response = authService.googleLogin("google-id-token");

        assertEquals(UserRole.RECEPTIONIST, response.user().role());
        assertEquals("google-sub-2", owner.getGoogleSub());
    }

    @Test
    void rejectsUnverifiedEmail() {
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("google-sub-3", owner.getEmail(), owner.getName(), false));

        assertThrows(InvalidGoogleTokenException.class, () -> authService.googleLogin("google-id-token"));
        verifyNoInteractions(users);
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void unknownGoogleUserIsNotProvisioned() {
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("google-sub-4", "unknown@example.com", "Unknown", true));
        when(users.findByGoogleSub("google-sub-4")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.googleLogin("google-id-token"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(users, never()).saveAndFlush(any());
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void inactiveAccountCannotSignIn() {
        owner.setActive(false);
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("google-sub-5", owner.getEmail(), owner.getName(), true));
        when(users.findByGoogleSub("google-sub-5")).thenReturn(Optional.of(owner));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.googleLogin("google-id-token"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void rejectsEmailBoundToAnotherGoogleSubject() {
        owner.setGoogleSub("previous-google-sub");
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("attacker-sub", owner.getEmail(), owner.getName(), true));
        when(users.findByGoogleSub("attacker-sub")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));

        assertThrows(InvalidGoogleTokenException.class, () -> authService.googleLogin("google-id-token"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsGoogleSubjectWhenItsEmailConflictsWithExistingBinding() {
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("google-sub-6", "different@example.com", "Owner", true));
        when(users.findByGoogleSub("google-sub-6")).thenReturn(Optional.of(owner));

        assertThrows(InvalidGoogleTokenException.class, () -> authService.googleLogin("google-id-token"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void databaseUniqueConstraintProtectsConcurrentIdentityLinking() {
        when(googleVerifier.verify("google-id-token")).thenReturn(
                new VerifiedGoogleIdentity("google-sub-7", owner.getEmail(), owner.getName(), true));
        when(users.findByGoogleSub("google-sub-7")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.saveAndFlush(owner)).thenThrow(new DataIntegrityViolationException("unique key"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.googleLogin("google-id-token"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void receptionistPreRegistrationDoesNotAcceptAClientSuppliedPassword() {
        CreateReceptionistRequest request = new CreateReceptionistRequest();
        request.setName("Front Desk");
        request.setEmail("desk@example.com");
        when(users.existsByEmailIgnoreCase("desk@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse.UserInfo response = authService.createReceptionist(request);

        assertEquals(UserRole.RECEPTIONIST, response.role());
        assertTrue(response.active());
        verify(users).saveAndFlush(argThat(user -> user.getPasswordHash() == null
                && user.getGoogleSub() == null && user.getEmail().equals("desk@example.com")));
    }

    @Test
    void bootstrapDoesNotPromoteAnyAccountAfterAnOwnerExists() {
        when(users.existsByRole(UserRole.OWNER)).thenReturn(true);

        authService.bootstrapInitialOwner("Someone Else", "person@example.com");

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void bootstrapFailsInsteadOfPromotingAnExistingReceptionist() {
        AppUser receptionist = new AppUser();
        receptionist.setEmail("person@example.com");
        receptionist.setRole(UserRole.RECEPTIONIST);
        when(users.existsByRole(UserRole.OWNER)).thenReturn(false);
        when(users.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(receptionist));

        assertThrows(IllegalStateException.class,
                () -> authService.bootstrapInitialOwner("Person", "person@example.com"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void roleChangeRevokesExistingTokensAndUsesPersistedRole() {
        AppUser receptionist = new AppUser();
        receptionist.setId(UUID.randomUUID());
        receptionist.setEmail("desk@example.com");
        receptionist.setName("Front Desk");
        receptionist.setRole(UserRole.RECEPTIONIST);
        receptionist.setActive(true);
        receptionist.setTokenVersion(4);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.findById(receptionist.getId())).thenReturn(Optional.of(receptionist));
        when(users.save(receptionist)).thenReturn(receptionist);

        AuthResponse.UserInfo response = authService.updateUserRole(owner.getEmail(), receptionist.getId(), UserRole.OWNER);

        assertEquals(UserRole.OWNER, response.role());
        assertEquals(5, receptionist.getTokenVersion());
    }

    @Test
    void lastOwnerCannotBeDemoted() {
        AppUser secondOwner = new AppUser();
        secondOwner.setId(UUID.randomUUID());
        secondOwner.setEmail("other-owner@example.com");
        secondOwner.setRole(UserRole.OWNER);
        secondOwner.setActive(true);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.findById(secondOwner.getId())).thenReturn(Optional.of(secondOwner));
        when(users.countByRoleAndActiveTrue(UserRole.OWNER)).thenReturn(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.updateUserRole(owner.getEmail(), secondOwner.getId(), UserRole.RECEPTIONIST));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(users, never()).save(any());
    }

    @Test
    void logoutIncrementsTokenVersionSoAllIssuedTokensAreRevoked() {
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.save(owner)).thenReturn(owner);

        authService.logout(owner.getEmail());

        assertEquals(1, owner.getTokenVersion());
        verify(users).save(owner);
    }
}
