package com.example.excelcompare.controller;

import com.example.excelcompare.model.FieldConfig;
import com.example.excelcompare.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class UploadController {

    private final ExcelService excelService;

    @GetMapping("/")
    public String index() {
        return "redirect:/upload";
    }

    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        return "upload";
    }

    @PostMapping("/upload")
    public String handleFileUpload(
            @RequestParam("file1") MultipartFile file1,
            @RequestParam("file2") MultipartFile file2,
            @RequestParam("fieldConfigs") List<FieldConfig> fieldConfigs,
            RedirectAttributes redirectAttributes) {

        // Validate files
        if (file1.isEmpty() || file2.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select both files");
            return "redirect:/upload";
        }

        try {
            // Process the comparison
            byte[] result = excelService.processExcelComparison(file1, file2, fieldConfigs);
            
            // Store the result in session for download
            redirectAttributes.addFlashAttribute("comparisonResult", result);
            redirectAttributes.addFlashAttribute("successMessage", "Comparison completed successfully!");
            
            return "redirect:/result";
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error processing files: " + e.getMessage());
            return "redirect:/upload";
        }
    }

    @GetMapping("/result")
    public String showResult() {
        return "result";
    }

    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> downloadFile(@ModelAttribute("comparisonResult") byte[] result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=comparison_result.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(result.length)
                .body(new ByteArrayResource(result));
    }
}