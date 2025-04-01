package com.example.excelcompare.model;

import lombok.Data;

@Data
public class FieldConfig {
    private String columnName;
    private String validationType; // NON_NULL, NUMERIC, REGEX
    private Boolean required;
    private Double min;
    private Double max;
    private String regex;
}