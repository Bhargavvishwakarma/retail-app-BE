package com.retail.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Calendar and timestamp helpers for the Sumangali store in India. */
public final class StoreTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private StoreTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
