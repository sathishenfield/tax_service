package com.example.tax.controller;

import com.core.lib.entity.TaxRecord;
import com.example.tax.service.TaxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tax")
@Tag(name = "Tax APIs", description = "Endpoints for tax calculation and retrieval")
public class TaxController {

    @Autowired
    private TaxService taxService;

    @PostMapping("/calculate")
    @Operation(summary = "Calculate tax for a user", description = "Calculates tax and saves TaxRecord")
    public TaxRecord calculateTax(@RequestParam String userName, @RequestParam double income) {
        return taxService.calculateTax(userName, income);
    }

    @GetMapping
    @Operation(summary = "Get tax record by username", description = "Fetches TaxRecord from cache or DB")
    public Optional<TaxRecord> getTaxRecord(@RequestParam String userName) {
        return taxService.getTaxRecord(userName);
    }

    @GetMapping("/list")
    @Operation(summary = "Get all tax records", description = "Retrieves all TaxRecords from DB")
    public List<TaxRecord> getTaxRecords() {
        return taxService.getTaxRecords();
    }
}
