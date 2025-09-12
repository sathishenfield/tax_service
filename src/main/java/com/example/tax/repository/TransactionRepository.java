package com.example.tax.repository;

import com.core.lib.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Transaction findByTransactionId(String transactionId);
}
