package com.paytm.mcpserver.elasticsearch.service;

import com.paytm.mcpserver.config.properties.ElasticsearchProperties;
import com.paytm.shared.elasticsearch.model.EsHostType;
import com.paytm.shared.mcp.tools.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Slf4j
public class EsHostSearchService implements McpTool {
    
    @Autowired
    private ElasticsearchProperties elasticsearchProperties;
    
    private static final DateTimeFormatter[] SUPPORTED_DATE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };
    
    // Fixed tertiary host start date
    private static final LocalDate TERTIARY_START_DATE = LocalDate.of(2023, 4, 1);
    
    @Override
    public String getName() {
        return "es_host_search";
    }
    
    /**
     * Select appropriate Elasticsearch host based on date range
     * 
     * @param startDate Optional start date (if null, returns PRIMARY)
     * @param endDate Optional end date (if null, uses startDate or current time)
     * @return Host selection result with type, configuration, and date range analysis
     */
    public Object executeHostSearch(String startDate, String endDate) {
        try {
            long executionStartTime = System.currentTimeMillis();
            
            log.debug("Executing host search with startDate: {}, endDate: {}", startDate, endDate);
            
            // If no date provided, return PRIMARY host
            if (startDate == null || startDate.trim().isEmpty()) {
                log.debug("No date provided, returning PRIMARY host");
                return buildHostResponse(EsHostType.PRIMARY, null, null, 
                    "No date range provided, defaulting to PRIMARY host", executionStartTime);
            }
            
            // Parse dates
            LocalDateTime parsedStartDate = parseDate(startDate);
            LocalDateTime parsedEndDate = endDate != null && !endDate.trim().isEmpty() 
                ? parseDate(endDate) : parsedStartDate;
            
            // Validate date range
            if (parsedEndDate.isBefore(parsedStartDate)) {
                throw new IllegalArgumentException("End date cannot be before start date");
            }
            
            // Calculate dynamic date ranges
            DateRanges dateRanges = calculateDateRanges();
            
            // Determine appropriate host based on date range
            HostSelectionResult result = selectHost(parsedStartDate, parsedEndDate, dateRanges);
            
            long executionTime = System.currentTimeMillis() - executionStartTime;
            
            log.debug("Host selection completed in {}ms, selected: {}", executionTime, result.selectedHost);
            
            return buildHostResponse(result.selectedHost, parsedStartDate, parsedEndDate, 
                result.reason, executionStartTime);
            
        } catch (Exception e) {
            log.error("Host search failed for startDate: {}, endDate: {}", startDate, endDate, e);
            return buildErrorResponse(e, startDate, endDate);
        }
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        String startDate = (String) parameters.get("startDate");
        String endDate = (String) parameters.get("endDate");
        
        return executeHostSearch(startDate, endDate);
    }
    
    private LocalDateTime parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }
        
        String trimmedDate = dateString.trim();
        
        // Try parsing with different formats
        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATS) {
            try {
                // Try parsing as LocalDateTime first
                try {
                    return LocalDateTime.parse(trimmedDate, formatter);
                } catch (DateTimeParseException e) {
                    // If that fails, try parsing as LocalDate and convert to LocalDateTime
                    LocalDate date = LocalDate.parse(trimmedDate, formatter);
                    return date.atStartOfDay();
                }
            } catch (DateTimeParseException e) {
                // Continue to next format
            }
        }
        
        throw new IllegalArgumentException("Unable to parse date: " + dateString + 
            ". Supported formats: yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd'T'HH:mm:ss, etc.");
    }
    
    private DateRanges calculateDateRanges() {
        LocalDateTime now = LocalDateTime.now();
        
        // PRIMARY: Current time - 6 months to now
        LocalDateTime primaryStart = now.minusMonths(6);
        LocalDateTime primaryEnd = now;
        
        // SECONDARY: 365 days before primary start to primary start
        LocalDateTime secondaryStart = primaryStart.minusDays(365);
        LocalDateTime secondaryEnd = primaryStart;
        
        // TERTIARY: Fixed start date (April 1, 2023) to secondary start
        LocalDateTime tertiaryStart = TERTIARY_START_DATE.atStartOfDay();
        LocalDateTime tertiaryEnd = secondaryStart;
        
        return new DateRanges(
            new DateRange(primaryStart, primaryEnd),
            new DateRange(secondaryStart, secondaryEnd),
            new DateRange(tertiaryStart, tertiaryEnd)
        );
    }
    
    private HostSelectionResult selectHost(LocalDateTime startDate, LocalDateTime endDate, DateRanges ranges) {
        // Check if the entire range falls within a single host's coverage
        
        // Check PRIMARY first (most recent data)
        if (isDateRangeWithin(startDate, endDate, ranges.primary)) {
            return new HostSelectionResult(EsHostType.PRIMARY, 
                "Date range falls entirely within PRIMARY host coverage (last 6 months)");
        }
        
        // Check SECONDARY
        if (isDateRangeWithin(startDate, endDate, ranges.secondary)) {
            return new HostSelectionResult(EsHostType.SECONDARY, 
                "Date range falls entirely within SECONDARY host coverage (6-18 months ago)");
        }
        
        // Check TERTIARY
        if (isDateRangeWithin(startDate, endDate, ranges.tertiary)) {
            return new HostSelectionResult(EsHostType.TERTIARY, 
                "Date range falls entirely within TERTIARY host coverage (April 2023 onwards)");
        }
        
        // If range spans multiple hosts, determine the best single host
        // Priority: PRIMARY > SECONDARY > TERTIARY (based on data freshness)
        
        if (hasOverlap(startDate, endDate, ranges.primary)) {
            return new HostSelectionResult(EsHostType.PRIMARY, 
                "Date range spans multiple hosts, selected PRIMARY for most recent data coverage");
        }
        
        if (hasOverlap(startDate, endDate, ranges.secondary)) {
            return new HostSelectionResult(EsHostType.SECONDARY, 
                "Date range spans multiple hosts, selected SECONDARY for best coverage");
        }
        
        if (hasOverlap(startDate, endDate, ranges.tertiary)) {
            return new HostSelectionResult(EsHostType.TERTIARY, 
                "Date range spans multiple hosts, selected TERTIARY for historical data coverage");
        }
        
        // If no overlap with any host (date range is outside all coverage)
        if (startDate.isAfter(ranges.primary.end)) {
            return new HostSelectionResult(EsHostType.PRIMARY, 
                "Date range is in the future, selected PRIMARY as default");
        } else {
            return new HostSelectionResult(EsHostType.TERTIARY, 
                "Date range is before all host coverage, selected TERTIARY for historical data");
        }
    }
    
    private boolean isDateRangeWithin(LocalDateTime startDate, LocalDateTime endDate, DateRange hostRange) {
        return !startDate.isBefore(hostRange.start) && !endDate.isAfter(hostRange.end);
    }
    
    private boolean hasOverlap(LocalDateTime startDate, LocalDateTime endDate, DateRange hostRange) {
        return !(endDate.isBefore(hostRange.start) || startDate.isAfter(hostRange.end));
    }
    
    private Map<String, Object> buildHostResponse(EsHostType selectedHost, 
                                                LocalDateTime startDate, 
                                                LocalDateTime endDate, 
                                                String reason,
                                                long executionStartTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("timestamp", Instant.now().toString());
        result.put("execution_time_ms", System.currentTimeMillis() - executionStartTime);
        
        // Selected host information
        result.put("selected_host", selectedHost.name());
        result.put("host_description", selectedHost.getDescription());
        result.put("selection_reason", reason);
        
        // Host configuration
        String hostUrl = selectedHost.getHostUrl(elasticsearchProperties);
        result.put("host_url", hostUrl);
        result.put("host_config", getHostConfig(selectedHost));
        
        // Input date range
        Map<String, Object> inputRange = new HashMap<>();
        inputRange.put("start_date", startDate != null ? startDate.toString() : null);
        inputRange.put("end_date", endDate != null ? endDate.toString() : null);
        result.put("input_date_range", inputRange);
        
        // Current date ranges for all hosts
        DateRanges ranges = calculateDateRanges();
        Map<String, Object> hostRanges = new HashMap<>();
        
        hostRanges.put("PRIMARY", Map.of(
            "start", ranges.primary.start.toString(),
            "end", ranges.primary.end.toString(),
            "description", "Last 6 months from current time"
        ));
        
        hostRanges.put("SECONDARY", Map.of(
            "start", ranges.secondary.start.toString(),
            "end", ranges.secondary.end.toString(),
            "description", "365 days before PRIMARY range"
        ));
        
        hostRanges.put("TERTIARY", Map.of(
            "start", ranges.tertiary.start.toString(),
            "end", ranges.tertiary.end.toString(),
            "description", "From April 1, 2023 to SECONDARY start"
        ));
        
        result.put("host_date_ranges", hostRanges);
        
        return result;
    }
    
    private Map<String, Object> getHostConfig(EsHostType hostType) {
        return elasticsearchProperties.getHosts().stream()
            .filter(host -> hostType.getName().equals(host.getName()))
            .findFirst()
            .map(host -> {
                Map<String, Object> config = new HashMap<>();
                config.put("name", host.getName());
                config.put("url", host.getUrl());
                config.put("timeout", host.getTimeout());
                return config;
            })
            .orElse(Map.of("error", "Host configuration not found for " + hostType.getName()));
    }
    
    private Map<String, Object> buildErrorResponse(Exception e, String startDate, String endDate) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("timestamp", Instant.now().toString());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("code", "HOST_SELECTION_FAILED");
        errorDetails.put("message", "Failed to select appropriate Elasticsearch host");
        errorDetails.put("details", e.getMessage());
        errorDetails.put("input_start_date", startDate);
        errorDetails.put("input_end_date", endDate);
        
        error.put("error", errorDetails);
        return error;
    }
    
    @Override
    public List<String> getRequiredParameters() {
        return Collections.emptyList(); // No required parameters
    }
    
    @Override
    public List<String> getOptionalParameters() {
        return Arrays.asList("startDate", "endDate");
    }
    
    // Helper classes
    private static class DateRange {
        final LocalDateTime start;
        final LocalDateTime end;
        
        DateRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
    
    private static class DateRanges {
        final DateRange primary;
        final DateRange secondary;
        final DateRange tertiary;
        
        DateRanges(DateRange primary, DateRange secondary, DateRange tertiary) {
            this.primary = primary;
            this.secondary = secondary;
            this.tertiary = tertiary;
        }
    }
    
    private static class HostSelectionResult {
        final EsHostType selectedHost;
        final String reason;
        
        HostSelectionResult(EsHostType selectedHost, String reason) {
            this.selectedHost = selectedHost;
            this.reason = reason;
        }
    }
}
