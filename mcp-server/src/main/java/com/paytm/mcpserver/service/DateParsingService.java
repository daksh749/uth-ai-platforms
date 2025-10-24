package com.paytm.mcpserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paytm.mcpserver.utility.DateFormatUtility;
import com.paytm.mcpserver.utility.ElasticsearchUtility;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Service for parsing and handling date inputs for Elasticsearch queries
 * 
 * Logic:
 * - If 2 dates provided: return both in ISO 8601 format
 * - If 1 date provided: treat as start date, end date = now
 * - If no dates provided: start = first of current month, end = now
 */
@Service
@Log4j2
public class DateParsingService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Parse dates based on user input
     * 
     * @param userPrompt Natural language prompt (for future LLM extraction)
     * @param explicitStartDate Explicit start date if provided
     * @param explicitEndDate Explicit end date if provided
     * @return JSON string with startDate and endDate in ISO 8601 format
     */
    public String parseDates(String userPrompt, String explicitStartDate, String explicitEndDate) {
        try {
            log.info("Parsing dates - Start: {}, End: {}, Prompt: {}", explicitStartDate, explicitEndDate, userPrompt);
            
            String startDate;
            String endDate;
            String source;
            
            // Scenario A: Both dates provided explicitly
            if (StringUtils.hasText(explicitStartDate) && StringUtils.hasText(explicitEndDate)) {
                startDate = parseAndFormatStartDate(explicitStartDate);
                endDate = parseAndFormatEndDate(explicitEndDate);
                source = "explicit_both";
                log.info("Using explicit start and end dates");
            }
            // Scenario B: Only start date provided
            else if (StringUtils.hasText(explicitStartDate) && !StringUtils.hasText(explicitEndDate)) {
                startDate = parseAndFormatStartDate(explicitStartDate);
                endDate = getCurrentDateTime();
                source = "explicit_start_only";
                log.info("Using explicit start date, end date = now");
            }
            // Scenario C: Only end date provided (treat as end date)
            else if (!StringUtils.hasText(explicitStartDate) && StringUtils.hasText(explicitEndDate)) {
                startDate = getStartOfCurrentMonth();
                endDate = parseAndFormatEndDate(explicitEndDate);
                source = "explicit_end_only";
                log.info("Using explicit end date, start date = start of month");
            }
            // Scenario D: No explicit dates - use defaults
            else {
                // Future enhancement: Extract dates from userPrompt using LLM
                startDate = getStartOfCurrentMonth();
                endDate = getCurrentDateTime();
                source = "default";
                log.info("No dates provided, using defaults (start of month to now)");
            }
            
            // Build response JSON
            return buildDateResponse(startDate, endDate, source);
            
        } catch (Exception e) {
            log.error("Failed to parse dates", e);
            return buildErrorResponse("Failed to parse dates: " + e.getMessage());
        }
    }
    
    /**
     * Parse and format start date to ISO 8601 with time at 00:00:00
     */
    private String parseAndFormatStartDate(String dateString) {
        try {
            LocalDate date = DateFormatUtility.parseDate(dateString);
            return DateFormatUtility.formatDate(date);
        } catch (Exception e) {
            log.error("Failed to parse start date: {}", dateString, e);
            throw new IllegalArgumentException("Invalid start date format: " + dateString);
        }
    }
    
    /**
     * Parse and format end date to ISO 8601 with time at 23:59:59
     */
    private String parseAndFormatEndDate(String dateString) {
        try {
            LocalDate date = DateFormatUtility.parseDate(dateString);
            return DateFormatUtility.formatDateEndOfDay(date);
        } catch (Exception e) {
            log.error("Failed to parse end date: {}", dateString, e);
            throw new IllegalArgumentException("Invalid end date format: " + dateString);
        }
    }
    
    /**
     * Get start of current month in ISO 8601 format
     */
    private String getStartOfCurrentMonth() {
        return ElasticsearchUtility.getDefaultStartDate();
    }
    
    /**
     * Get current date and time in ISO 8601 format
     */
    private String getCurrentDateTime() {
        return ElasticsearchUtility.getDefaultEndDate();
    }
    
    /**
     * Build successful date response JSON
     */
    private String buildDateResponse(String startDate, String endDate, String source) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("startDate", startDate);
            response.put("endDate", endDate);
            response.put("source", source);
            response.put("timezone", "Asia/Kolkata");
            response.put("status", "success");
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to build date response", e);
            return buildErrorResponse("Failed to build response");
        }
    }
    
    /**
     * Build error response JSON
     */
    private String buildErrorResponse(String errorMessage) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("error", errorMessage);
            response.put("status", "error");
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"error\":\"Failed to parse dates\",\"status\":\"error\"}";
        }
    }
}

