package com.retail.service;

import com.retail.dto.SaleRequest;
import com.retail.model.PaymentMethod;
import com.retail.model.Product;
import com.retail.model.Sale;
import com.retail.notification.event.NotificationBusinessEventPublisher;
import com.retail.notification.event.NotificationBusinessEvent;
import com.retail.repository.CustomerRepository;
import com.retail.repository.ProductRepository;
import com.retail.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceNotificationEventTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private NotificationBusinessEventPublisher notificationEventPublisher;
    @InjectMocks
    private SaleService saleService;

    @Test
    void failedSaleValidationDoesNotPublishAnyNotificationEvent() {
        UUID productId = UUID.randomUUID();
        Product product = activeProduct(productId, 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> saleService.createSale(saleRequest(productId, 3)));

        verify(notificationEventPublisher, never()).publish(any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void successfulSalePublishesCompletionAndInventoryContractsAfterSavingTheSale() {
        UUID productId = UUID.randomUUID();
        Product product = activeProduct(productId, 7);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            sale.setId(UUID.randomUUID());
            return sale;
        });

        saleService.createSale(saleRequest(productId, 2));

        ArgumentCaptor<NotificationBusinessEvent> events = ArgumentCaptor.forClass(NotificationBusinessEvent.class);
        verify(notificationEventPublisher, org.mockito.Mockito.times(2)).publish(events.capture());
        verify(saleRepository).save(any(Sale.class));
        assertTrue(events.getAllValues().stream()
                .anyMatch(com.retail.notification.event.SaleCompletedEvent.class::isInstance));
        assertTrue(events.getAllValues().stream()
                .anyMatch(com.retail.notification.event.InventoryStockChangedEvent.class::isInstance));
    }

    private Product activeProduct(UUID id, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName("Notebook");
        product.setActive(true);
        product.setStockQuantity(stock);
        product.setCost(BigDecimal.ZERO);
        product.setPrice(BigDecimal.TEN);
        return product;
    }

    private SaleRequest saleRequest(UUID productId, int quantity) {
        SaleRequest.Item item = new SaleRequest.Item();
        item.setProductId(productId);
        item.setQuantity(quantity);
        SaleRequest request = new SaleRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setItems(List.of(item));
        return request;
    }
}
