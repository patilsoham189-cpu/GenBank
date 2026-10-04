package com.genbank.apnabank.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_tx_account", columnList = "accountNumber"),
    @Index(name = "idx_tx_created", columnList = "createdAt"),
    @Index(name = "idx_tx_user", columnList = "user_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_tx_ref_account", columnNames = {"referenceId", "accountNumber"})
})
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String referenceId;

    @Column(nullable = false)
    private String accountNumber;

    private String counterpartyAccount;

    private String counterpartyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private String narration;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Transaction(String referenceId, String accountNumber, String counterpartyAccount,
                       String counterpartyName, TransactionType type, BigDecimal amount,
                       BigDecimal balanceAfter, String narration) {
        this.referenceId = referenceId;
        this.accountNumber = accountNumber;
        this.counterpartyAccount = counterpartyAccount;
        this.counterpartyName = counterpartyName;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.narration = narration;
        this.createdAt = LocalDateTime.now();
    }

    public Transaction(Long userId, String referenceId, String accountNumber, String counterpartyAccount,
                       String counterpartyName, TransactionType type, BigDecimal amount,
                       BigDecimal balanceAfter, String narration) {
        this(referenceId, accountNumber, counterpartyAccount, counterpartyName, type, amount, balanceAfter, narration);
        this.userId = userId;
    }
}
