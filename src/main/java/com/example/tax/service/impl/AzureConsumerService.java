//package com.example.tax.service.impl;
//
//import com.azure.spring.messaging.servicebus.implementation.core.annotation.ServiceBusListener;
//import com.core.lib.entity.TaxRecord;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.log4j.Log4j2;
//import org.springframework.stereotype.Service;
//
//@Service
//@Log4j2
//public class AzureConsumerService {
//    private final ObjectMapper objectMapper;
//
//    public AzureConsumerService(ObjectMapper objectMapper) {
//        this.objectMapper = objectMapper;
//    }
//
//    @ServiceBusListener(destination = "${spring.cloud.azure.servicebus.queue-name}")
//    public void receiveMessage(String message) {
//        try {
//            TaxRecord record = objectMapper.readValue(message, TaxRecord.class);
//            log.info("Received message: {}", record);
//        } catch (Exception e) {
//            log.error("Error while consuming message",e);
//        }
//    }
//}
