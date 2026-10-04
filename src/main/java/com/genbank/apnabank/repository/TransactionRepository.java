package com.genbank.apnabank.repository;

import com.genbank.apnabank.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
    List<Transaction> findTop50ByOrderByCreatedAtDesc();
}
