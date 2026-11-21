package com.retail.service;

import com.retail.config.CacheNames;
import com.retail.model.Product;
import com.retail.notification.event.InventoryChangeSource;
import com.retail.notification.event.InventoryStockChangedEvent;
import com.retail.notification.event.NotificationBusinessEventPublisher;
import com.retail.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final NotificationBusinessEventPublisher notificationEventPublisher;

    public List<Product> findAll(String search) {
        if (search != null && !search.isBlank()) {
            return productRepository.searchActive(search.trim());
        }
        return productRepository.findByActiveTrue();
    }

    public Page<Product> findPage(String search, int page, int size, boolean includeOutOfStock) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        if (search != null && !search.isBlank()) {
            return productRepository.searchActive(search.trim(), pageable);
        }
        if (!includeOutOfStock) {
            return productRepository.findByActiveTrueAndStockQuantityGreaterThan(0, pageable);
        }
        return productRepository.findByActiveTrue(pageable);
    }

    public List<Product> findLowStock(int threshold) {
        return productRepository.findByActiveTrueAndStockQuantityLessThan(threshold);
    }

    public Product findById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_SUMMARY, key = "T(com.retail.util.StoreTime).today()"),
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_OPERATIONAL, key = "T(com.retail.util.StoreTime).today()"),
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_ANALYTICS, allEntries = true)
    })
    public Product create(Product product) {
        validateMinimumPrice(product);
        product.setId(null);
        return productRepository.save(product);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_SUMMARY, key = "T(com.retail.util.StoreTime).today()"),
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_OPERATIONAL, key = "T(com.retail.util.StoreTime).today()"),
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_ANALYTICS, allEntries = true)
    })
    @Transactional
    public Product update(UUID id, Product request) {
        validateMinimumPrice(request);
        Product existing = findById(id);
        Integer previousStockQuantity = existing.getStockQuantity();
        existing.setName(request.getName());
        existing.setSku(request.getSku());
        existing.setDescription(request.getDescription());
        existing.setSize(request.getSize());
        existing.setColor(request.getColor());
        existing.setPrice(request.getPrice());
        existing.setCost(request.getCost());
        existing.setStockQuantity(request.getStockQuantity());
        existing.setActive(request.getActive());
        existing.setCategory(request.getCategory());
        Product updated = productRepository.save(existing);
        Integer currentStockQuantity = updated.getStockQuantity();
        if (previousStockQuantity != null && currentStockQuantity != null
                && !previousStockQuantity.equals(currentStockQuantity)) {
            notificationEventPublisher.publish(new InventoryStockChangedEvent(
                    UUID.randomUUID(), updated.getId(), updated.getName(), previousStockQuantity,
                    currentStockQuantity, InventoryChangeSource.MANUAL_ADJUSTMENT));
        }
        return updated;
    }

    private void validateMinimumPrice(Product product) {
        BigDecimal cost = product.getCost() == null ? BigDecimal.ZERO : product.getCost();
        BigDecimal minimumPrice = cost.multiply(new BigDecimal("1.15")).setScale(2, RoundingMode.CEILING);
        if (product.getPrice() == null || product.getPrice().compareTo(minimumPrice) < 0) {
            throw new IllegalArgumentException("Price must be at least Rs. " + minimumPrice + " (cost plus 15% profit)");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_SUMMARY, key = "T(com.retail.util.StoreTime).today()"),
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_OPERATIONAL, key = "T(com.retail.util.StoreTime).today()"),
            @CacheEvict(cacheNames = CacheNames.DASHBOARD_ANALYTICS, allEntries = true)
    })
    public void delete(UUID id) {
        Product product = findById(id);
        product.setActive(false);
        productRepository.save(product);
    }
}
