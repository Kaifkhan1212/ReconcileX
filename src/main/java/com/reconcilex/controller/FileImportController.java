package com.reconcilex.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.reconcilex.dto.ImportSummaryResponse;
import com.reconcilex.service.FileImportService;

@RestController
@RequestMapping("/api/imports")
public class FileImportController {

    private final FileImportService fileImportService;

    public FileImportController(FileImportService fileImportService) {
        this.fileImportService = fileImportService;
    }

    @PostMapping(value = "/bank", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportSummaryResponse> importBankStatement(
            @RequestParam("file") MultipartFile file) {
        ImportSummaryResponse response = fileImportService.importBankStatement(file);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/internal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportSummaryResponse> importInternalTransactions(
            @RequestParam("file") MultipartFile file) {
        ImportSummaryResponse response = fileImportService.importInternalTransactions(file);
        return ResponseEntity.ok(response);
    }
}
