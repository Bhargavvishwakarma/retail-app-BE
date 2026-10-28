package com.retail.controller;

import com.retail.dto.AuthResponse;
import com.retail.dto.CreateReceptionistRequest;
import com.retail.dto.LoginRequest;
import com.retail.dto.UpdateWorkspacePreferencesRequest;
import com.retail.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @GetMapping("/me")
    public AuthResponse.UserInfo me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
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
            int pageSize = size == null ? 24 : Math.min(100, Math.max(1, size));
            return authService.listUsers(pageNumber, pageSize);
        }
        return authService.listUsers();
    }

    @PostMapping("/receptionists")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse.UserInfo createReceptionist(@Valid @RequestBody CreateReceptionistRequest request) {
        return authService.createReceptionist(request);
    }
}
