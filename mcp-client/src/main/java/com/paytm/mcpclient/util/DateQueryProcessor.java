package com.paytm.mcpclient.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility service for processing dates in Elasticsearch queries.
 * Handles conversion between different date formats and applies business rules.
 * 
 * Supported Formats:
 * - dd/MM/yyyy (for LLM query generation)
 * - yyyy-MM-dd (for MCP tool parameters)  
 * - Epoch milliseconds (for final ES query execution)
 * 
 * Business Rules:
 * 1. Single date: gte = that date (00:00:00), lte = today (23:59:59)
 * 2. Two dates: smaller date = gte (00:00:00), larger date = lte (23:59:59)  
 * 3. No date: gte = 1st day of current month (00:00:00), lte = today (23:59:59)
 * 4. Relative dates: Calculate based on current date
 * 
 * Timezone: Asia/Kolkata (IST)
 */
@Component
@Slf4j
public class DateQueryProcessor {

    private static final ZoneId INDIAN_TIMEZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DD_MM_YYYY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YYYY_MM_DD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    // Regex patterns for extracting dates from queries
    private static final Pattern DD_MM_YYYY_PATTERN = Pattern.compile("\"(\\d{2}/\\d{2}/\\d{4})\"");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("\"gte\"\\s*:\\s*\"([^\"]+)\"|\"lte\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Extract dates from an Elasticsearch query string that contains dd/MM/yyyy format dates
     */
    public DateRange extractDatesFromQuery(String queryJson) {
        
        String gte = null;
        String lte = null;
        
        try {
            // Look for gte and lte values in the query
            Matcher rangeMatcher = DATE_RANGE_PATTERN.matcher(queryJson);
            while (rangeMatcher.find()) {
                if (rangeMatcher.group(1) != null) {
                    gte = rangeMatcher.group(1);
                }
                if (rangeMatcher.group(2) != null) {
                    lte = rangeMatcher.group(2);
                }
            }
            
            // Also look for direct dd/MM/yyyy patterns
            if (gte == null || lte == null) {
                Matcher dateMatcher = DD_MM_YYYY_PATTERN.matcher(queryJson);
                if (dateMatcher.find()) {
                    if (gte == null) gte = dateMatcher.group(1);
                    if (lte == null && dateMatcher.find()) {
                        lte = dateMatcher.group(1);
                    }
                }
            }
            
            return new DateRange(gte, lte);
            
        } catch (Exception e) {
            log.error("Failed to extract dates from query", e);
            return new DateRange(null, null);
        }
    }

    /**
     * Apply business rules to extracted dates
     * Rules:
     * 1. No date: gte = 1st of current month (00:00:00), lte = current datetime (now)
     * 2. Single date: gte = that date (00:00:00), lte = current datetime (now)
     * 3. Two dates: gte = smaller date (00:00:00), lte = larger date (23:59:59)
     */
    public DateRange applyDateRules(String gte, String lte) {
        
        LocalDate today = LocalDate.now();
        String firstOfMonthStr = today.withDayOfMonth(1).format(DD_MM_YYYY_FORMATTER);
        
        // Special marker for "current time" vs "end of day"
        String CURRENT_TIME_MARKER = "NOW";
        
        try {
            // Rule 1: No dates specified - use current month start to now
            if (gte == null && lte == null) {
                return new DateRange(firstOfMonthStr, CURRENT_TIME_MARKER);
            }
            
            // Rule 2: Single date specified - use provided date to now
            if (gte != null && lte == null) {
                return new DateRange(gte, CURRENT_TIME_MARKER);
            }
            
            // Handle case where only lte is provided (use first of month to lte)
            if (gte == null && lte != null) {
                return new DateRange(firstOfMonthStr, lte);
            }
            
            // Rule 3: Two dates specified - use both dates (smaller to larger)
            if (gte != null && lte != null) {
                LocalDate gteDate = parseDate(gte);
                LocalDate lteDate = parseDate(lte);
                
                if (gteDate.isAfter(lteDate)) {
                    return new DateRange(lte, gte);
                }
                
                return new DateRange(gte, lte);
            }
            
        } catch (Exception e) {
            log.error("Failed to apply date rules, using fallback", e);
        }
        
        // Fallback: current month start to now
        return new DateRange(firstOfMonthStr, CURRENT_TIME_MARKER);
    }

    /**
     * Convert dd/MM/yyyy date to epoch milliseconds
     * Special handling for "NOW" marker to use current datetime
     */
    public long convertToEpoch(String ddMmYyyy, boolean isStartOfDay) {
        
        try {
            // Handle special "NOW" marker for current datetime
            if ("NOW".equals(ddMmYyyy)) {
                LocalDateTime currentDateTime = LocalDateTime.now();
                return currentDateTime.atZone(INDIAN_TIMEZONE).toInstant().toEpochMilli();
            }
            
            LocalDate date = parseDate(ddMmYyyy);
            LocalDateTime dateTime;
            
            if (isStartOfDay) {
                dateTime = date.atStartOfDay(); // 00:00:00
            } else {
                dateTime = date.atTime(23, 59, 59, 999_000_000); // 23:59:59.999
            }
            
            long epochMillis = dateTime.atZone(INDIAN_TIMEZONE).toInstant().toEpochMilli();
            
            return epochMillis;
            
        } catch (Exception e) {
            log.error("Failed to convert {} to epoch", ddMmYyyy, e);
            throw new RuntimeException("Failed to convert date to epoch: " + ddMmYyyy, e);
        }
    }

    /**
     * Convert epoch milliseconds to yyyy-MM-dd format (for MCP tool parameters)
     */
    public String convertEpochToYyyyMmDd(long epochMillis) {
        try {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), INDIAN_TIMEZONE);
            return dateTime.toLocalDate().format(YYYY_MM_DD_FORMATTER);
        } catch (Exception e) {
            log.error("Failed to convert epoch {} to yyyy-MM-dd", epochMillis, e);
            return LocalDate.now().format(YYYY_MM_DD_FORMATTER);
        }
    }

    /**
     * Replace dd/MM/yyyy dates in query with epoch milliseconds
     */
    public String replaceDatesWithEpoch(String queryJson, long gteEpoch, long lteEpoch) {
        
        try {
            String updatedQuery = queryJson;
            
            // Replace gte values
            updatedQuery = updatedQuery.replaceAll(
                "\"gte\"\\s*:\\s*\"[^\"]+\"", 
                String.format("\"gte\": %d", gteEpoch)
            );
            
            // Replace lte values  
            updatedQuery = updatedQuery.replaceAll(
                "\"lte\"\\s*:\\s*\"[^\"]+\"",
                String.format("\"lte\": %d", lteEpoch)
            );
            
            // Replace any remaining dd/MM/yyyy patterns with epoch
            updatedQuery = updatedQuery.replaceAll(
                "\"\\d{2}/\\d{2}/\\d{4}\"",
                String.valueOf(gteEpoch) // Use gte as default
            );
            
            return updatedQuery;
            
        } catch (Exception e) {
            log.error("Failed to replace dates with epochs in query", e);
            return queryJson; // Return original if replacement fails
        }
    }

    /**
     * Parse dd/MM/yyyy date string to LocalDate
     */
    private LocalDate parseDate(String ddMmYyyy) {
        try {
            return LocalDate.parse(ddMmYyyy, DD_MM_YYYY_FORMATTER);
        } catch (DateTimeParseException e) {
            log.error("Failed to parse date: {}", ddMmYyyy, e);
            throw new RuntimeException("Invalid date format: " + ddMmYyyy + ". Expected dd/MM/yyyy", e);
        }
    }

    /**
     * Get current date range for fallback scenarios
     */
    public DateRange getCurrentMonthRange() {
        LocalDate today = LocalDate.now();
        String firstOfMonth = today.withDayOfMonth(1).format(DD_MM_YYYY_FORMATTER);
        
        return new DateRange(firstOfMonth, "NOW");
    }

    /**
     * Data class for holding date ranges
     */
    @Data
    public static class DateRange {
        private final String gte;
        private final String lte;
        
        public DateRange(String gte, String lte) {
            this.gte = gte;
            this.lte = lte;
        }
        
        public boolean hasGte() {
            return gte != null && !gte.trim().isEmpty();
        }
        
        public boolean hasLte() {
            return lte != null && !lte.trim().isEmpty();
        }
        
        public boolean hasAnyDate() {
            return hasGte() || hasLte();
        }
        
        public boolean hasBothDates() {
            return hasGte() && hasLte();
        }
        
        @Override
        public String toString() {
            return String.format("DateRange{gte='%s', lte='%s'}", gte, lte);
        }
    }

}
