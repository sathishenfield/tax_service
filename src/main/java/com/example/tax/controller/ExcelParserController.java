package com.example.tax.controller;

import com.example.tax.service.ExcelParserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@Tag(name = "Excel Parser", description = "APIs for uploading and processing Excel files")
@RestController
@RequestMapping("/api/excel")
public class ExcelParserController {
    @Autowired
    private ExcelParserService excelParserService;

    @Operation(
            summary = "Upload Excel file",
            description = "Uploads an Excel file and processes its contents"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Excel processed successfully"),
            @ApiResponse(responseCode = "500", description = "Error processing Excel",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadExcel(@Parameter(description = "Excel file to be uploaded", required = true)
            @RequestParam("file") MultipartFile file) {
//        try {
            excelParserService.processExcel(file);
            return ResponseEntity.ok("Excel processed successfully");
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Error processing Excel: " + e.getMessage());
//        }
    }
}
