package com.example.tax.service.impl;

import com.core.lib.exception.BusinessException;
import com.example.tax.service.ComputationService;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class KafkaConsumerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final ComputationService computationService;

    public KafkaConsumerService(KafkaTemplate<String, Object> kafkaTemplate, ComputationService computationService) {
        this.kafkaTemplate = kafkaTemplate;
        this.computationService = computationService;
    }

    @KafkaListener(topics = "tax_calculation", groupId = "tax_calculation_group")
    public void listenTaxCalculation(ConsumerRecord<String, Object> record) {
        try {
            Object value = record.value();
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> request = castToStringObjectMap(mapValue);
                computationService.computeTaxCalculation(request);
            } else {
                log.warn("Unexpected message type received: {}, message={}",
                        value != null ? value.getClass().getName() : "null", value);
            }
        } catch (BusinessException e) {
            log.error("Business error while processing Kafka message at offset {}: {}", record.offset(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while processing Kafka message at offset {}: {}", record.offset(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "tax_request", groupId = "tax_processor_group")
    public void listen(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        log.info("Received message: {}", value);
        if (value instanceof Map<?, ?> mapValue) {
            processRecord(castToStringObjectMap(mapValue));
        } else if (value instanceof List<?> listValue) {
            listValue.forEach(item -> {
                if (item instanceof Map<?, ?> mapItem) {
                    processRecord(castToStringObjectMap(mapItem));
                } else {
                    log.warn("Unexpected list element type: {}", item.getClass());
                }
            });
        } else {
            log.warn("Unexpected message type: {}", value != null ? value.getClass() : "null");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private void processRecord(Map<String, Object> recordMap) {
        Map<String, Object> modifiableMap = new HashMap<>(recordMap);

        String userName = (String) modifiableMap.get("userName");
        if (userName == null) {
            log.warn("Skipping record with missing userName: {}", modifiableMap);
            return;
        }

        double income = parseDouble(modifiableMap.get("income"));
        double tax = calculateTax(income);
        double netIncome = income - tax;

        modifiableMap.put("tax", tax);
        modifiableMap.put("netIncome", netIncome);

        log.info("Processed TaxRecord for '{}': Tax={}, NetIncome={}", userName, tax, netIncome);

        kafkaTemplate.send("tax_response", modifiableMap);
    }

    private double parseDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (Exception e) {
            log.warn("Failed to parse income: {}", value, e);
            return 0;
        }
    }

    public double calculateTax(double income) {
        if (income <= 250_000) return 0;
        else if (income <= 500_000) return (income - 250_000) * 0.05;
        else if (income <= 1_000_000) return (250_000 * 0.05) + (income - 500_000) * 0.2;
        else return (250_000 * 0.05) + (500_000 * 0.2) + (income - 1_000_000) * 0.3;
    }
}
