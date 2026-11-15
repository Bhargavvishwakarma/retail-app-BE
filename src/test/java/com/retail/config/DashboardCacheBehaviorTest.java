package com.retail.config;

import com.retail.dto.DashboardMetricsProjection;
import com.retail.dto.SaleRequest;
import com.retail.model.Customer;
import com.retail.model.PaymentMethod;
import com.retail.model.Product;
import com.retail.model.Sale;
import com.retail.notification.event.NotificationBusinessEventPublisher;
import com.retail.repository.CustomerRepository;
import com.retail.repository.DashboardSummaryRepository;
import com.retail.repository.ProductRepository;
import com.retail.repository.SaleRepository;
import com.retail.service.DashboardService;
import com.retail.service.CustomerService;
import com.retail.service.ProductService;
import com.retail.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DashboardCacheBehaviorTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.cache.dashboard-summary.ttl-seconds=1",
        "app.cache.dashboard-summary.maximum-size=2",
        "app.cache.dashboard-operational.ttl-seconds=1",
        "app.cache.dashboard-operational.maximum-size=2"
})
class DashboardCacheBehaviorTest {
    private static final Duration TEST_TTL = Duration.ofSeconds(1);

    @jakarta.annotation.Resource
    private DashboardService dashboardService;

    @jakarta.annotation.Resource
    private ProductService productService;

    @jakarta.annotation.Resource
    private CustomerService customerService;

    @jakarta.annotation.Resource
    private SaleService saleService;

    @jakarta.annotation.Resource
    private CacheManager cacheManager;

    @jakarta.annotation.Resource
    private DashboardSummaryRepository dashboardSummaryRepository;

    @jakarta.annotation.Resource
    private SaleRepository saleRepository;

    @jakarta.annotation.Resource
    private ProductRepository productRepository;

    @jakarta.annotation.Resource
    private CustomerRepository customerRepository;

    @BeforeEach
    void resetCachesAndRepositories() {
        cacheManager.getCache(CacheNames.DASHBOARD_SUMMARY).clear();
        cacheManager.getCache(CacheNames.DASHBOARD_OPERATIONAL).clear();
        reset(dashboardSummaryRepository, saleRepository, productRepository, customerRepository);

        DashboardMetricsProjection metrics = new DashboardMetricsProjection() {
            @Override public BigDecimal getTodayRevenue() { return BigDecimal.TEN; }
            @Override public long getTodaySalesCount() { return 1; }
            @Override public long getTotalProducts() { return 2; }
            @Override public long getLowStockCount() { return 1; }
            @Override public long getTotalCustomers() { return 3; }
        };
        when(dashboardSummaryRepository.loadMetrics(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(metrics);
        when(saleRepository.findRecentSummaries(any(Pageable.class))).thenReturn(List.of());
    }

    @Test
    void repeatedDashboardRequestsHitTheCacheAndKeepSummaryVariantsSeparate() {
        Map<String, Object> summary = dashboardService.getSummary();
        dashboardService.getSummary();
        dashboardService.getOperationalSummary();
        dashboardService.getOperationalSummary();

        verify(dashboardSummaryRepository, times(2))
                .loadMetrics(any(LocalDateTime.class), any(LocalDateTime.class), anyInt());
        verify(saleRepository, times(1)).findRecentSummaries(any(Pageable.class));
        assertEquals(6, summary.size());
        assertTrue(summary.containsKey("recentSales"));
        assertThrows(UnsupportedOperationException.class, () -> summary.put("unexpected", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<?>) summary.get("recentSales")).add(null));
        assertNotNull(cacheManager.getCache(CacheNames.DASHBOARD_SUMMARY).get(LocalDate.now()));
        assertNull(cacheManager.getCache(CacheNames.DASHBOARD_SUMMARY).get(LocalDate.now().minusDays(1)));
    }

    @Test
    void productWriteEvictsDashboardEntriesOnlyAfterSuccessfulCompletion() {
        dashboardService.getSummary();
        dashboardService.getSummary();

        Product invalid = new Product();
        invalid.setCost(BigDecimal.TEN);
        invalid.setPrice(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> productService.create(invalid));

        dashboardService.getSummary();
        verify(dashboardSummaryRepository, times(1))
                .loadMetrics(any(LocalDateTime.class), any(LocalDateTime.class), anyInt());

        Product valid = new Product();
        valid.setName("Notebook");
        valid.setCost(BigDecimal.ZERO);
        valid.setPrice(BigDecimal.TEN);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Product.class));
        productService.create(valid);

        dashboardService.getSummary();
        verify(dashboardSummaryRepository, times(2))
                .loadMetrics(any(LocalDateTime.class), any(LocalDateTime.class), anyInt());
    }

    @Test
    void customerAndSaleWritesEvictDashboardEntries() {
        dashboardService.getSummary();
        dashboardService.getSummary();

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Customer.class));
        customerService.create(new Customer());
        dashboardService.getSummary();
        customerService.delete(UUID.randomUUID());
        dashboardService.getSummary();

        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("Notebook");
        product.setActive(true);
        product.setStockQuantity(20);
        product.setCost(BigDecimal.ZERO);
        product.setPrice(BigDecimal.TEN);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Product.class));
        when(saleRepository.save(any(Sale.class)))
                .thenAnswer(invocation -> {
                    Sale saved = invocation.getArgument(0, Sale.class);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        SaleRequest.Item item = new SaleRequest.Item();
        item.setProductId(productId);
        item.setQuantity(1);
        SaleRequest request = new SaleRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setItems(List.of(item));
        saleService.createSale(request);
        dashboardService.getSummary();

        verify(dashboardSummaryRepository, times(4))
                .loadMetrics(any(LocalDateTime.class), any(LocalDateTime.class), anyInt());
    }

    @Test
    void dashboardCacheExpiresAtTheConfiguredTtl() throws InterruptedException {
        dashboardService.getSummary();
        Thread.sleep(TEST_TTL.toMillis() + 100);

        dashboardService.getSummary();

        verify(dashboardSummaryRepository, times(2))
                .loadMetrics(any(LocalDateTime.class), any(LocalDateTime.class), anyInt());
    }

    @Test
    void caffeineCacheHonorsItsMaximumEntryCount() {
        var localManager = CacheConfiguration.createLocalCacheManager(TEST_TTL, 2, TEST_TTL, 2);
        CaffeineCache cache = (CaffeineCache) localManager.getCache(CacheNames.DASHBOARD_SUMMARY);
        var nativeCache = cache.getNativeCache();

        cache.put("first", 1);
        cache.put("second", 2);
        cache.put("third", 3);
        nativeCache.cleanUp();

        assertTrue(nativeCache.estimatedSize() <= 2);
    }

    @Test
    void transactionAwareEvictionWaitsForCommitAndDoesNotRunOnRollback() {
        var localManager = CacheConfiguration.createLocalCacheManager(TEST_TTL, 2, TEST_TTL, 2);
        var awareManager = new TransactionAwareCacheManagerProxy(localManager);
        var cache = awareManager.getCache(CacheNames.DASHBOARD_SUMMARY);
        cache.put("commit", 1);
        cache.put("rollback", 2);

        TransactionSynchronizationManager.initSynchronization();
        cache.evict("commit");
        assertNotNull(cache.get("commit"));
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.clearSynchronization();
        assertNull(cache.get("commit"));

        TransactionSynchronizationManager.initSynchronization();
        cache.evict("rollback");
        assertNotNull(cache.get("rollback"));
        TransactionSynchronizationManager.clearSynchronization();
        assertNotNull(cache.get("rollback"));
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Configuration
    @Import({CacheConfiguration.class, DashboardService.class, ProductService.class,
            CustomerService.class, SaleService.class})
    static class TestConfig {
        @Bean DashboardSummaryRepository dashboardSummaryRepository() { return mock(DashboardSummaryRepository.class); }
        @Bean SaleRepository saleRepository() { return mock(SaleRepository.class); }
        @Bean ProductRepository productRepository() { return mock(ProductRepository.class); }
        @Bean CustomerRepository customerRepository() { return mock(CustomerRepository.class); }
        @Bean NotificationBusinessEventPublisher notificationBusinessEventPublisher() {
            return mock(NotificationBusinessEventPublisher.class);
        }
    }

}
