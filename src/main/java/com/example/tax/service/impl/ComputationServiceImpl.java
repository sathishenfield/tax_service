package com.example.tax.service.impl;

import com.core.lib.entity.Transaction;
import com.core.lib.exception.BusinessException;
import com.example.tax.repository.TransactionRepository;
import com.example.tax.service.ComputationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Log4j2
public class ComputationServiceImpl implements ComputationService {


    private final TransactionRepository transactionRepository;

    public ComputationServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void computeTaxCalculation(Map<String, Object> request) {
        String transactionId = (String) request.get("transaction_id");
        try {
            if (transactionId == null || transactionId.isBlank()) {
                log.warn("Transaction ID is missing in request: {}", request);
                return;
            }

            Transaction transaction = transactionRepository.findByTransactionId(transactionId);
            if (transaction == null) {
                log.warn("No transaction found for ID: {}", transactionId);
                return;
            }

            if (transaction.getAmount() == null) {
                log.warn("Transaction {} has no amount, skipping tax calculation", transactionId);
                return;
            }

            double taxAmount = calculateTax(transaction.getAmount());
            transaction.setTaxAmount(taxAmount);
            transactionRepository.save(transaction);
        } catch (BusinessException e) {
            log.error("Business error while processing tax calculation for transaction {}: {}",
                    transactionId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during tax calculation for transaction {}: {}",
                    transactionId, e.getMessage(), e);
        }
    }

    private double calculateTax(double income) {
        if (income <= 250_000) return 0;
        else if (income <= 500_000) return (income - 250_000) * 0.05;
        else if (income <= 1_000_000) return (250_000 * 0.05) + (income - 500_000) * 0.2;
        else return (250_000 * 0.05) + (500_000 * 0.2) + (income - 1_000_000) * 0.3;
    }
}
