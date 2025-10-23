package com.paytm.mcpserver.utility;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for handling ISO 8601 date format consistently across the application
 */
public class DateFormatUtility {
    
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    public static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    /**
     * Parse date string in ISO 8601 format to LocalDate
     * Supports both full ISO 8601 (2025-01-15T00:00:00+05:30) and date-only (2025-01-15)
     * 
     * @param dateString Date in ISO 8601 format (e.g., "2025-01-15T00:00:00+05:30" or "2025-01-15")
     * @return LocalDate object
     * @throws IllegalArgumentException if date format is invalid
     */
    public static LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }
        
        try {
            String trimmed = dateString.trim();
            // If it contains 'T', parse as full ISO 8601
            if (trimmed.contains("T")) {
                return ZonedDateTime.parse(trimmed, ISO_8601_FORMATTER).toLocalDate();
            } else {
                // Parse as simple date
                return LocalDate.parse(trimmed);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                String.format("Invalid date format: '%s'. Expected ISO 8601 format (e.g., 2025-01-15T00:00:00+05:30 or 2025-01-15)", 
                             dateString), e);
        }
    }
    
    /**
     * Format LocalDate to ISO 8601 string with time at start of day (00:00:00)
     * 
     * @param date LocalDate object
     * @return Date string in ISO 8601 format (e.g., "2025-01-15T00:00:00+05:30")
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return date.atStartOfDay(IST_ZONE).format(ISO_8601_FORMATTER);
    }
    
    /**
     * Format LocalDate to ISO 8601 string with time at end of day (23:59:59)
     * 
     * @param date LocalDate object
     * @return Date string in ISO 8601 format (e.g., "2025-01-15T23:59:59+05:30")
     */
    public static String formatDateEndOfDay(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return date.atTime(23, 59, 59).atZone(IST_ZONE).format(ISO_8601_FORMATTER);
    }
    
    /**
     * Validate if a date string is in correct ISO 8601 format
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
     * Get current date and time in ISO 8601 format
     * 
     * @return Current date and time as ISO 8601 format string
     */
    public static String getCurrentDate() {
        return ZonedDateTime.now(IST_ZONE).format(ISO_8601_FORMATTER);
    }
    
    /**
     * Get date N days ago in ISO 8601 format
     * 
     * @param daysAgo Number of days to subtract from current date
     * @return Date string in ISO 8601 format
     */
    public static String getDateDaysAgo(int daysAgo) {
        return formatDate(LocalDate.now(IST_ZONE).minusDays(daysAgo));
    }
}
