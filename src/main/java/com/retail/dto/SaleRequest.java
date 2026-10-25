package com.retail.dto;

import com.retail.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SaleRequest {

    private UUID customerId;

    @NotNull
    private PaymentMethod paymentMethod;

    @NotEmpty
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        @NotNull
        private UUID productId;

        @NotNull
        @Min(1)
        private Integer quantity;

        private BigDecimal unitPrice;
    }
}
