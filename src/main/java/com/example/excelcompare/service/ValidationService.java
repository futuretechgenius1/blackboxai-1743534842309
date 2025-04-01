package com.example.excelcompare.service;

import org.springframework.stereotype.Service;
import com.example.excelcompare.exception.ValidationException;

@Service
public class ValidationService {

    public boolean validateNonNull(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public boolean validateNumericInRange(String value, double min, double max) {
        try {
            double numericValue = Double.parseDouble(value);
            return numericValue >= min && numericValue <= max;
        } catch (NumberFormatException e) {
            throw new ValidationException("Value must be a valid number");
        }
    }

    public boolean validateByRegex(String value, String regex) {
        if (value == null) {
            return false;
        }
        return value.matches(regex);
    }
}