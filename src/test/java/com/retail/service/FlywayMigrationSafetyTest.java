package com.retail.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationSafetyTest {
    @Test
    void googleIdentityMigrationPreservesRowsAndAddsAccessColumnsWithSafeDefaults() throws IOException {
        String path = "/db/migration/V4__add_google_identity_and_account_access.sql";
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "V4 migration must be available to Flyway");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertTrue(sql.contains("ADD COLUMN GOOGLE_SUB"));
            assertTrue(sql.contains("ADD COLUMN ACTIVE BOOLEAN NOT NULL DEFAULT TRUE"));
            assertTrue(sql.contains("ADD COLUMN TOKEN_VERSION INTEGER NOT NULL DEFAULT 0"));
            assertTrue(sql.contains("ALTER COLUMN PASSWORD_HASH DROP NOT NULL"));
            assertTrue(sql.contains("CREATE UNIQUE INDEX UQ_APP_USERS_GOOGLE_SUB"));
            assertFalse(sql.matches("(?s).*\\b(DROP TABLE|DROP COLUMN|TRUNCATE|DELETE FROM)\\b.*"));
        }
    }
}
