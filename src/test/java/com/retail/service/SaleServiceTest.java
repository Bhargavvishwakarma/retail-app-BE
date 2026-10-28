package com.retail.service;

import com.retail.repository.CustomerRepository;
import com.retail.repository.ProductRepository;
import com.retail.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private SaleService saleService;

    @Test
    void findPageUsesTypedDateBoundsWhenNoDateFilterIsProvided() {
        when(saleRepository.findSummariesFiltered(any(LocalDateTime.class), any(LocalDateTime.class), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        saleService.findPage(0, 20);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(saleRepository).findSummariesFiltered(fromCaptor.capture(), toCaptor.capture(), org.mockito.ArgumentMatchers.eq(""), any(Pageable.class));

        assertEquals(LocalDateTime.of(1, 1, 1, 0, 0), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999), toCaptor.getValue());
    }
}
