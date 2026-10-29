package com.retail.controller;

import com.retail.dto.AuthResponse;
import com.retail.dto.CreateReceptionistRequest;
import com.retail.dto.GoogleLoginRequest;
import com.retail.dto.LoginRequest;
import com.retail.dto.UpdateWorkspacePreferencesRequest;
import com.retail.dto.UpdateUserRoleRequest;
import com.retail.dto.UpdateUserStatusRequest;
import com.retail.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @PostMapping("/google")
    public AuthResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request.idToken());
    }

    @GetMapping("/me")
    public AuthResponse.UserInfo me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/preferences")
    public AuthResponse.UserInfo updatePreferences(Authentication authentication,
                                                   @Valid @RequestBody UpdateWorkspacePreferencesRequest request) {
        return authService.updateWorkspaceTheme(authentication.getName(), request.workspaceTheme());
    }

    @GetMapping("/users")
    public Object users(@RequestParam(required = false) Integer page,
                        @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int pageNumber = page == null ? 0 : Math.max(0, page);
            int pageSize = size == null ? 10 : Math.min(100, Math.max(1, size));
            return authService.listUsers(pageNumber, pageSize);
        }
        return authService.listUsers();
    }

    @PostMapping("/receptionists")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse.UserInfo createReceptionist(@Valid @RequestBody CreateReceptionistRequest request) {
        return authService.createReceptionist(request);
    }

    @PatchMapping("/users/{userId}/status")
    public AuthResponse.UserInfo updateUserStatus(Authentication authentication, @PathVariable UUID userId,
                                                  @Valid @RequestBody UpdateUserStatusRequest request) {
        return authService.updateUserStatus(authentication.getName(), userId, request.active());
    }

    @PatchMapping("/users/{userId}/role")
    public AuthResponse.UserInfo updateUserRole(Authentication authentication, @PathVariable UUID userId,
                                                @Valid @RequestBody UpdateUserRoleRequest request) {
        return authService.updateUserRole(authentication.getName(), userId, request.role());
    }
}
