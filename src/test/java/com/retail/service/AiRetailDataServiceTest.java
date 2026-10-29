package com.retail.service;

import com.retail.dto.AiDataQueryRequest;
import com.retail.model.AppUser;
import com.retail.model.UserRole;
import com.retail.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRetailDataServiceTest {
    @Test
    void recentSalesAppliesRequestedExclusiveDateRangeAndReturnsLocalTimestampText() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AppUserRepository users = mock(AppUserRepository.class);
        byte[] secret = new byte[32];
        String encodedSecret = java.util.Base64.getEncoder().encodeToString(secret);
        ActorAssertionService assertions = new ActorAssertionService(encodedSecret);
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setRole(UserRole.OWNER);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        AiRetailDataService service = new AiRetailDataService(jdbc, users, assertions);
        LocalDate from = LocalDate.of(2026, 10, 29);
        LocalDate to = from.plusDays(1);
        AiDataQueryRequest request = new AiDataQueryRequest(AiDataQueryRequest.Operation.RECENT_SALES,
                null, from, to, null, 10, null);
        when(jdbc.queryForList(contains("from sales where sale_date"), any(Object[].class)))
                .thenReturn(List.of());

        List<?> result = service.query(request, assertions.mint(user));

        assertEquals(List.of(), result);
        verify(jdbc).queryForList(contains("to_char(sale_date, 'YYYY-MM-DD\"T\"HH24:MI:SS')"),
                eq(from), eq(to), eq(10));
    }
}
