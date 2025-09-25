package com.paytm.mcpclient.strategy;

import com.paytm.mcpclient.intent.IntentAnalysisResult;
import com.paytm.mcpclient.intent.UserIntent;
import com.paytm.mcpclient.mcp.service.McpClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for handling HOST_ONLY intent.
 * This strategy finds the optimal Elasticsearch host for a given date range or criteria.
 * 
 * Examples of user prompts that trigger this strategy:
 * - "best host for September data"
 * - "which host should I use"
 * - "optimal server for this query"
 * - "recommend host for date range"
 */
@Component
@Slf4j
public class HostOnlyStrategy implements IntentExecutionStrategy {

    private final McpClientService mcpClientService;
    
    // Date patterns for extracting dates from user prompts
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b|" +
        "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\w*\\s+(\\d{4})\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern MONTH_YEAR_PATTERN = Pattern.compile(
        "\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\w*\\s+(\\d{4})\\b",
        Pattern.CASE_INSENSITIVE
    );

    public HostOnlyStrategy(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }

    @Override
    public UserIntent getSupportedIntent() {
        return UserIntent.HOST_ONLY;
    }

    @Override
    public Object execute(String userPrompt, IntentAnalysisResult analysisResult) {
        log.info("Executing HOST_ONLY strategy for prompt: {}", userPrompt);
        
        long startTime = System.currentTimeMillis();
        
        try {
            DateRange dateRange = extractDateRange(userPrompt);
            Object hostResult = mcpClientService.searchElasticsearchHost(dateRange.startDate, dateRange.endDate);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Return structured response
            return buildSuccessResponse(userPrompt, hostResult, dateRange, executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Host selection failed after {}ms", executionTime, e);
            
            return buildErrorResponse(e, executionTime, userPrompt, analysisResult);
        }
    }

    /**
     * Extract date range from user prompt or use defaults
     */
    private DateRange extractDateRange(String userPrompt) {
        String startDate = null;
        String endDate = null;
        
        // Try to extract specific dates
        Matcher dateMatcher = DATE_PATTERN.matcher(userPrompt);
        if (dateMatcher.find()) {
            if (dateMatcher.group(1) != null) {
                // Format: dd/MM/yyyy or dd-MM-yyyy
                startDate = String.format("%s-%02d-%02d", 
                    dateMatcher.group(3), 
                    Integer.parseInt(dateMatcher.group(2)), 
                    Integer.parseInt(dateMatcher.group(1)));
            } else if (dateMatcher.group(4) != null) {
                // Format: 1st Sep 2025
                int month = getMonthNumber(dateMatcher.group(5));
                startDate = String.format("%s-%02d-%02d", 
                    dateMatcher.group(6), month, Integer.parseInt(dateMatcher.group(4)));
            }
        }
        
        // Try to extract month/year (e.g., "September 2025")
        if (startDate == null) {
            Matcher monthMatcher = MONTH_YEAR_PATTERN.matcher(userPrompt);
            if (monthMatcher.find()) {
                int month = getMonthNumber(monthMatcher.group(1));
                int year = Integer.parseInt(monthMatcher.group(2));
                startDate = String.format("%d-%02d-01", year, month);
                
                // End date is last day of the month
                LocalDate lastDayOfMonth = LocalDate.of(year, month, 1).withDayOfMonth(
                    LocalDate.of(year, month, 1).lengthOfMonth()
                );
                endDate = lastDayOfMonth.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
        }
        
        // Use default range if no dates found
        if (startDate == null) {
            LocalDate now = LocalDate.now();
            LocalDate firstDayOfMonth = now.withDayOfMonth(1);
            startDate = firstDayOfMonth.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            endDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
        }
        
        // If only start date, use today as end date
        if (endDate == null) {
            endDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        
        return new DateRange(startDate, endDate);
    }

    /**
     * Convert month name to number
     */
    private int getMonthNumber(String monthName) {
        return switch (monthName.toLowerCase().substring(0, 3)) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> 1;
        };
    }

    private Map<String, Object> buildSuccessResponse(String userPrompt, Object hostResult, DateRange dateRange, long executionTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("intent", "HOST_ONLY");
        response.put("userPrompt", userPrompt);
        response.put("executionTime", executionTime + "ms");
        response.put("result", Map.of(
            "type", "host_recommendation",
            "data", hostResult,
            "dateRange", Map.of(
                "startDate", dateRange.startDate,
                "endDate", dateRange.endDate
            )
        ));
        
        return response;
    }

    private Map<String, Object> buildErrorResponse(Exception e, long executionTime, String userPrompt, IntentAnalysisResult analysisResult) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("intent", "HOST_ONLY");
        response.put("userPrompt", userPrompt);
        response.put("executionTime", executionTime + "ms");
        response.put("error", Map.of(
            "message", e.getMessage(),
            "type", e.getClass().getSimpleName()
        ));
        
        return response;
    }

    @Override
    public String getDescription() {
        return "Finds optimal Elasticsearch host for given date range";
    }

    /**
     * Simple data class for date range
     */
    private static class DateRange {
        final String startDate;
        final String endDate;
        
        DateRange(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
