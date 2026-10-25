package com.retail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateWorkspacePreferencesRequest(
        @NotBlank
        @Pattern(regexp = "(?i)forest|ocean|berry|clay")
        String workspaceTheme
) {}
