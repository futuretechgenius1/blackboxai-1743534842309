package com.example.excelcompare.service;

import com.example.excelcompare.exception.ValidationException;
import com.example.excelcompare.model.FieldConfig;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private final ValidationService validationService;

    public byte[] processExcelComparison(MultipartFile file1, MultipartFile file2, List<FieldConfig> fieldConfigs) throws IOException {
        // Read both workbooks
        Workbook workbook1 = WorkbookFactory.create(file1.getInputStream());
        Workbook workbook2 = WorkbookFactory.create(file2.getInputStream());

        // Get the first sheet from each workbook
        Sheet sheet1 = workbook1.getSheetAt(0);
        Sheet sheet2 = workbook2.getSheetAt(0);

        // Create maps to store the data from both sheets
        Map<String, Map<String, String>> data1 = readSheetData(sheet1, fieldConfigs);
        Map<String, Map<String, String>> data2 = readSheetData(sheet2, fieldConfigs);

        // Compare and create output workbook
        return generateComparisonWorkbook(data1, data2, fieldConfigs);
    }

    private Map<String, Map<String, String>> readSheetData(Sheet sheet, List<FieldConfig> fieldConfigs) {
        Map<String, Map<String, String>> data = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        
        // Create column index mapping
        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                columnIndexMap.put(cell.getStringCellValue(), i);
            }
        }

        // Read data rows
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Map<String, String> rowData = new HashMap<>();
            String keyValue = null;

            for (FieldConfig config : fieldConfigs) {
                Integer columnIndex = columnIndexMap.get(config.getColumnName());
                if (columnIndex == null) {
                    throw new ValidationException("Column " + config.getColumnName() + " not found in the Excel file");
                }

                Cell cell = row.getCell(columnIndex);
                String value = getCellValueAsString(cell);

                // Validate the value based on configuration
                validateField(value, config);

                rowData.put(config.getColumnName(), value);
                
                // Use the first column as the key for comparison
                if (keyValue == null) {
                    keyValue = value;
                }
            }

            if (keyValue != null) {
                data.put(keyValue, rowData);
            }
        }

        return data;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private void validateField(String value, FieldConfig config) {
        switch (config.getValidationType()) {
            case "NON_NULL":
                if (config.getRequired() && !validationService.validateNonNull(value)) {
                    throw new ValidationException("Field " + config.getColumnName() + " cannot be null or empty");
                }
                break;
            case "NUMERIC":
                if (config.getMin() != null && config.getMax() != null) {
                    if (!validationService.validateNumericInRange(value, config.getMin(), config.getMax())) {
                        throw new ValidationException("Field " + config.getColumnName() + " must be between " + config.getMin() + " and " + config.getMax());
                    }
                }
                break;
            case "REGEX":
                if (config.getRegex() != null && !validationService.validateByRegex(value, config.getRegex())) {
                    throw new ValidationException("Field " + config.getColumnName() + " does not match the required pattern");
                }
                break;
        }
    }

    private byte[] generateComparisonWorkbook(
            Map<String, Map<String, String>> data1,
            Map<String, Map<String, String>> data2,
            List<FieldConfig> fieldConfigs) throws IOException {
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Differences");

        // Create header row
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        int columnIndex = 0;
        // Add source indicator column
        Cell sourceHeaderCell = headerRow.createCell(columnIndex++);
        sourceHeaderCell.setCellValue("Source");
        sourceHeaderCell.setCellStyle(headerStyle);
        
        // Add configured columns
        for (FieldConfig config : fieldConfigs) {
            Cell cell = headerRow.createCell(columnIndex++);
            cell.setCellValue(config.getColumnName());
            cell.setCellStyle(headerStyle);
        }

        // Add differences to the sheet
        int rowNum = 1;
        CellStyle diffStyle = createDiffStyle(workbook);

        // Process all keys from both datasets
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(data1.keySet());
        allKeys.addAll(data2.keySet());

        for (String key : allKeys) {
            Map<String, String> row1 = data1.get(key);
            Map<String, String> row2 = data2.get(key);

            // If key exists only in first file
            if (row2 == null) {
                Row row = sheet.createRow(rowNum++);
                populateRow(row, "File 1 Only", row1, fieldConfigs, diffStyle);
                continue;
            }

            // If key exists only in second file
            if (row1 == null) {
                Row row = sheet.createRow(rowNum++);
                populateRow(row, "File 2 Only", row2, fieldConfigs, diffStyle);
                continue;
            }

            // If key exists in both files, check for differences
            boolean hasDifferences = false;
            for (FieldConfig config : fieldConfigs) {
                String value1 = row1.get(config.getColumnName());
                String value2 = row2.get(config.getColumnName());
                if (!Objects.equals(value1, value2)) {
                    hasDifferences = true;
                    break;
                }
            }

            if (hasDifferences) {
                // Add row from first file
                Row row1Output = sheet.createRow(rowNum++);
                populateRow(row1Output, "File 1", row1, fieldConfigs, diffStyle);
                
                // Add row from second file
                Row row2Output = sheet.createRow(rowNum++);
                populateRow(row2Output, "File 2", row2, fieldConfigs, diffStyle);
            }
        }

        // Auto-size columns
        for (int i = 0; i < fieldConfigs.size() + 1; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createDiffStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void populateRow(Row row, String source, Map<String, String> data, List<FieldConfig> fieldConfigs, CellStyle style) {
        int columnIndex = 0;
        
        // Add source indicator
        Cell sourceCell = row.createCell(columnIndex++);
        sourceCell.setCellValue(source);
        sourceCell.setCellStyle(style);
        
        // Add data cells
        for (FieldConfig config : fieldConfigs) {
            Cell cell = row.createCell(columnIndex++);
            cell.setCellValue(data.get(config.getColumnName()));
            cell.setCellStyle(style);
        }
    }
}