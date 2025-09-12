package com.example.tax.service;

import org.springframework.web.multipart.MultipartFile;

public interface ExcelParserService {

    void processExcel(MultipartFile file);
}
