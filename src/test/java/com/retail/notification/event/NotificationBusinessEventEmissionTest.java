package com.retail.notification.event;

import com.retail.dto.SaleRequest;
import com.retail.model.PaymentMethod;
import com.retail.model.Product;
import com.retail.model.Sale;
import com.retail.repository.CustomerRepository;
import com.retail.repository.ProductRepository;
import com.retail.repository.SaleRepository;
import com.retail.service.SaleService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationBusinessEventEmissionTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private NotificationBusinessEventPublisher eventPublisher;

    @InjectMocks
    private SaleService saleService;

    @Test
    void successfulSaleEmitsCommittedSaleAndInventoryContracts() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 10, new BigDecimal("10000"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(saleRepository.save(org.mockito.ArgumentMatchers.any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            sale.setId(UUID.randomUUID());
            return sale;
        });

        saleService.createSale(saleRequest(productId, 1));

        ArgumentCaptor<NotificationBusinessEvent> events = ArgumentCaptor.forClass(NotificationBusinessEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publish(events.capture());
        List<NotificationBusinessEvent> published = events.getAllValues();
        SaleCompletedEvent saleCompleted = (SaleCompletedEvent) published.get(0);
        InventoryStockChangedEvent inventoryChanged = (InventoryStockChangedEvent) published.get(1);
        assertEquals(new BigDecimal("10000"), saleCompleted.totalAmount());
        assertEquals(10, inventoryChanged.previousQuantity());
        assertEquals(9, inventoryChanged.currentQuantity());
    }

    @Test
    void failedSaleValidationDoesNotEmitAnOutboxContract() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId, 0, BigDecimal.TEN)));

        assertThrows(IllegalArgumentException.class, () -> saleService.createSale(saleRequest(productId, 1)));

        verify(eventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
        verify(saleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Product product(UUID id, int stock, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setName("Notebook");
        product.setActive(true);
        product.setStockQuantity(stock);
        product.setCost(BigDecimal.ZERO);
        product.setPrice(price);
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
