package com.example.tax.service.impl;

import com.core.lib.entity.TaxRecord;
import com.core.lib.exception.BusinessException;
import com.core.lib.util.RedisCacheProvider;
import com.example.tax.repository.TaxRecordRepository;
import com.example.tax.service.TaxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TaxServiceImpl implements TaxService {

    @Autowired
    private TaxRecordRepository taxRecordRepository;

    @Autowired
    private RedisCacheProvider redisCacheProvider;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaConsumerService kafkaConsumerService;

    // Map to track pending async tax calculations
    private final Map<Long, CompletableFuture<TaxRecord>> pendingRecords = new ConcurrentHashMap<>();

    // Calculate tax synchronously for a single user and save
    @Override
    public TaxRecord calculateTax(String userName, double income) {
        if (StringUtils.isBlank(userName)) {
            log.warn("Tax calculation failed: userName is blank");
            throw new BusinessException("1001", "User name cannot be empty");
        }
        log.info("Starting tax calculation for user: {}, income: {}", userName, income);

        double tax = kafkaConsumerService.calculateTax(income);
        double netIncome = income - tax;

        TaxRecord record = TaxRecord.builder()
                .userName(userName)
                .income(income)
                .taxAmount(tax)
                .netIncome(netIncome)
                .build();

        TaxRecord savedRecord = taxRecordRepository.save(record);
        log.info("Persisted TaxRecord in DB for user={}", savedRecord.getUserName());

        redisCacheProvider.addData("tax", savedRecord.getUserName(), savedRecord);

        log.info("Cached TaxRecord for user={}", savedRecord.getUserName());
        return savedRecord;
    }

    @Override
    public Optional<TaxRecord> getTaxRecord(String userName) {
        log.info("Fetching TaxRecord for user={}", userName);
        Optional<TaxRecord> cached = redisCacheProvider.getData("tax", userName, TaxRecord.class);
        if (cached.isPresent()) {
            log.info("Cache hit for user={}", userName);
            return cached;
        }

        Optional<TaxRecord> dbRecord = taxRecordRepository.findByUserNameIgnoreCase(userName);

        if (dbRecord.isPresent()) {
            log.info("Found TaxRecord in DB for user={}", userName);
            redisCacheProvider.addData("tax", userName, dbRecord.get());
            log.info("Cached DB TaxRecord for user={}", userName);
        } else {
            log.warn("No TaxRecord found for user={} in cache or DB", userName);
        }

        return dbRecord;
    }

    // Get all records and process via Kafka asynchronously
    @Override
    public List<TaxRecord> getTaxRecords() {
        log.info("Fetching TaxRecord for all users");
        List<TaxRecord> records = taxRecordRepository.findAll();
        List<CompletableFuture<TaxRecord>> futures = new ArrayList<>();

        for (TaxRecord record : records) {
            CompletableFuture<TaxRecord> future = new CompletableFuture<>();
            pendingRecords.put(record.getId(), future);
            futures.add(future);

            Map<String, Object> request = Map.of(
                    "id", record.getId(),
                    "userName", record.getUserName(),
                    "income", record.getIncome()
            );

            kafkaTemplate.send("tax_request", request);
            log.info("Sent tax request to Kafka for user: {}", record.getUserName());
        }

        // Wait for async responses with timeout
        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("Failed to get response for a tax record", e);
                        return null;
                    }
                })

                .filter(Objects::nonNull)
                .toList();
    }

    // Kafka listener for tax responses
    @KafkaListener(topics = "tax_response", groupId = "tax_api_group")
    public void listenResponse(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (!(value instanceof Map<?, ?> mapValue)) return;

        Map<String, Object> response = (Map<String, Object>) mapValue;
        Long id = ((Number) response.get("id")).longValue();

        CompletableFuture<TaxRecord> future = pendingRecords.remove(id);
        if (future != null) {
            TaxRecord updated = TaxRecord.builder()
                    .id(id)
                    .userName((String) response.get("userName"))
                    .income(((Number) response.get("income")).doubleValue())
                    .taxAmount(((Number) response.get("tax")).doubleValue())
                    .netIncome(((Number) response.get("netIncome")).doubleValue())
                    .build();

            // Complete the future so that getTaxRecords can return it
            future.complete(updated);

            // Update DB and cache to persist async calculation
            taxRecordRepository.save(updated);
            redisCacheProvider.addData("tax", updated.getUserName(), updated);

            log.info("Updated TaxRecord from Kafka response: {}", updated);
        }
    }
}