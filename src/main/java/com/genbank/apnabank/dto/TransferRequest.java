package com.genbank.apnabank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * DTO for fund transfer requests. Validated before processing.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Beneficiary account number is required")
    private String beneficiaryAccount;

    @NotBlank(message = "IFSC code is required")
    private String ifscCode;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum transfer amount is ₹1.00")
    private BigDecimal amount;
}
