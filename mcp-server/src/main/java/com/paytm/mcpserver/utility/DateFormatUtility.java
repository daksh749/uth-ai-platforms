package com.paytm.mcpserver.utility;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for handling DD-MM-YYYY date format consistently across the application
 */
public class DateFormatUtility {
    
    public static final String DATE_FORMAT_PATTERN = "dd-MM-yyyy";
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN);
    
    /**
     * Parse date string in DD-MM-YYYY format to LocalDate
     * 
     * @param dateString Date in DD-MM-YYYY format (e.g., "15-01-2025")
     * @return LocalDate object
     * @throws IllegalArgumentException if date format is invalid
     */
    public static LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }
        
        try {
            return LocalDate.parse(dateString.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                String.format("Invalid date format: '%s'. Expected format: %s (e.g., 15-01-2025)", 
                             dateString, DATE_FORMAT_PATTERN), e);
        }
    }
    
    /**
     * Format LocalDate to DD-MM-YYYY string
     * 
     * @param date LocalDate object
     * @return Date string in DD-MM-YYYY format
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * Validate if a date string is in correct DD-MM-YYYY format
     * 
     * @param dateString Date string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidDateFormat(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return false;
        }
        
        try {
            parseDate(dateString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Get current date in DD-MM-YYYY format
     * 
     * @return Current date as DD-MM-YYYY string
     */
    public static String getCurrentDate() {
        return formatDate(LocalDate.now());
    }
    
    /**
     * Get date N days ago in DD-MM-YYYY format
     * 
     * @param daysAgo Number of days to subtract from current date
     * @return Date string in DD-MM-YYYY format
     */
    public static String getDateDaysAgo(int daysAgo) {
        return formatDate(LocalDate.now().minusDays(daysAgo));
    }
}
