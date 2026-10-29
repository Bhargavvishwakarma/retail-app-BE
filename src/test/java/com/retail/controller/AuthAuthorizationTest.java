package com.retail.controller;

import com.retail.config.SecurityConfig;
import com.retail.dto.AuthResponse;
import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.repository.AppUserRepository;
import com.retail.service.AuthService;
import com.retail.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthAuthorizationTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserRepository userRepository;

    @Test
    void unauthenticatedCallsReceive401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validBearerTokenAuthenticatesAgainstTheCurrentActiveUser() throws Exception {
        UUID ownerId = UUID.randomUUID();
        AppUser owner = new AppUser();
        owner.setId(ownerId);
        owner.setName("Shop Owner");
        owner.setEmail("owner@example.com");
        owner.setRole(UserRole.OWNER);
        owner.setActive(true);
        owner.setTokenVersion(0);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(ownerId.toString());
        when(claims.get("ver", Integer.class)).thenReturn(0);
        when(jwtService.parse("valid-owner-token")).thenReturn(claims);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(authService.currentUser("owner@example.com")).thenReturn(new AuthResponse.UserInfo(
                ownerId, "Shop Owner", "owner@example.com", UserRole.OWNER,
                "forest", null, true));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer valid-owner-token"))
                .andExpect(status().isOk());

        verify(userRepository).findById(ownerId);
        verify(authService).currentUser("owner@example.com");
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCannotListStaffOrChangeRoles() throws Exception {
        mvc.perform(get("/api/auth/users")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/auth/users/{id}/role", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/auth/users/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCanInviteReceptionistUsingVerifiedEmailPreRegistration() throws Exception {
        when(authService.createReceptionist(any())).thenReturn(new AuthResponse.UserInfo(
                UUID.randomUUID(), "Front Desk", "desk@example.com", UserRole.RECEPTIONIST,
                "forest", null, true));

        mvc.perform(post("/api/auth/receptionists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Front Desk\",\"email\":\"desk@example.com\"}"))
                .andExpect(status().isCreated());

        verify(authService).createReceptionist(any());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCanListUsers() throws Exception {
        when(authService.listUsers()).thenReturn(List.of());

        mvc.perform(get("/api/auth/users")).andExpect(status().isOk());

        verify(authService).listUsers();
    }
}
