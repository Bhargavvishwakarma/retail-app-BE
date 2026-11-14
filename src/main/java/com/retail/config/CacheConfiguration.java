package com.retail.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfiguration {

    @Bean
    CacheManager cacheManager(
            @Value("${app.cache.dashboard-summary.ttl-seconds:60}") long summaryTtlSeconds,
            @Value("${app.cache.dashboard-summary.maximum-size:10}") long summaryMaximumSize,
            @Value("${app.cache.dashboard-operational.ttl-seconds:60}") long operationalTtlSeconds,
            @Value("${app.cache.dashboard-operational.maximum-size:10}") long operationalMaximumSize,
            @Value("${app.cache.dashboard-analytics.ttl-seconds:60}") long analyticsTtlSeconds,
            @Value("${app.cache.dashboard-analytics.maximum-size:20}") long analyticsMaximumSize) {
        SimpleCacheManager localCacheManager = createLocalCacheManager(
                Duration.ofSeconds(summaryTtlSeconds), summaryMaximumSize,
                Duration.ofSeconds(operationalTtlSeconds), operationalMaximumSize,
                Duration.ofSeconds(analyticsTtlSeconds), analyticsMaximumSize);
        return new TransactionAwareCacheManagerProxy(localCacheManager);
    }

    static SimpleCacheManager createLocalCacheManager(Duration summaryTtl, long summaryMaximumSize,
                                                       Duration operationalTtl, long operationalMaximumSize) {
        return createLocalCacheManager(summaryTtl, summaryMaximumSize, operationalTtl, operationalMaximumSize,
                Duration.ofSeconds(60), 20);
    }

    static SimpleCacheManager createLocalCacheManager(Duration summaryTtl, long summaryMaximumSize,
                                                       Duration operationalTtl, long operationalMaximumSize,
                                                       Duration analyticsTtl, long analyticsMaximumSize) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                caffeineCache(CacheNames.DASHBOARD_SUMMARY, summaryTtl, summaryMaximumSize),
                caffeineCache(CacheNames.DASHBOARD_OPERATIONAL, operationalTtl, operationalMaximumSize),
                caffeineCache(CacheNames.DASHBOARD_ANALYTICS, analyticsTtl, analyticsMaximumSize)));
        try {
            cacheManager.afterPropertiesSet();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize Caffeine caches", exception);
        }
        return cacheManager;
    }

    private static CaffeineCache caffeineCache(String name, Duration ttl, long maximumSize) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive for " + name);
        }
        if (maximumSize < 1) {
            throw new IllegalArgumentException("Cache maximum size must be positive for " + name);
        }
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .recordStats()
                .build(), false);
    }
}
