package com.retail.service;

import com.retail.notification.event.NotificationBusinessEventPublisher;
import com.retail.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private NotificationBusinessEventPublisher notificationEventPublisher;

    @InjectMocks
    private ProductService productService;

    @Test
    void excludesOutOfStockProductsWhenBrowsingThePosCatalog() {
        productService.findPage("", 0, 12, false);

        verify(productRepository).findByActiveTrueAndStockQuantityGreaterThan(eq(0), any());
    }

    @Test
    void includesOutOfStockProductsWhenSearchingThePosCatalog() {
        productService.findPage("sold out item", 0, 12, false);

        verify(productRepository).searchActive(eq("sold out item"), any());
    }
}
