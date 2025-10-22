package com.paytm.mcpserver.service;

import com.paytm.mcpserver.enums.EsHostEnum;
import com.paytm.mcpserver.utility.ElasticsearchUtility;
import com.paytm.mcpserver.utility.DateFormatUtility;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for selecting optimal Elasticsearch hosts based on date ranges
 * 
 * Host Selection Logic:
 * - PRIMARY: Recent data (last 6 months)
 * - SECONDARY: Medium-term data (6 months to 1 year ago)  
 * - TERTIARY: Historical data (older than 1 year, from April 2023)
 * 
 * Returns multiple hosts if the date range spans across different host periods.
 */
@Service
public class ElasticsearchHostSelector {
    
    private static final LocalDate TERTIARY_START_DATE = LocalDate.of(2023, 4, 1);
    
    // Inner record classes to keep everything in one file
    public record DateRange(LocalDateTime start, LocalDateTime end) {
        @Override
        public String toString() {
            return String.format("%s to %s", start.toLocalDate(), end.toLocalDate());
        }
    }
    
    public record HostCoverage(EsHostEnum host, LocalDateTime startDate, LocalDateTime endDate) {}
    
    /**
     * Select optimal hosts based on start and end date
     * Returns list of HostCoverage objects if date range spans across different periods
     */
    public List<HostCoverage> selectHost(String startDate, String endDate) {
        try {

            if(StringUtils.isEmpty(startDate)){
                startDate = ElasticsearchUtility.getDefaultStartDate();
            }
            if(StringUtils.isEmpty(endDate)){
                endDate = ElasticsearchUtility.getDefaultEndDate();
            }

            // Parse dates in ISO 8601 format
            LocalDateTime start = DateFormatUtility.parseDate(startDate).atStartOfDay();
            LocalDateTime end = DateFormatUtility.parseDate(endDate).atTime(23, 59, 59);
            
            // Calculate date ranges
            LocalDateTime now = LocalDateTime.now();
            DateRange primaryRange = new DateRange(now.minusMonths(6), now);
            DateRange secondaryRange = new DateRange(now.minusMonths(6).minusDays(365), now.minusMonths(6));
            DateRange tertiaryRange = new DateRange(TERTIARY_START_DATE.atStartOfDay(), now.minusMonths(6).minusDays(365));
            
            // Select multiple hosts
            List<HostCoverage> coverages = new ArrayList<>();
            LocalDateTime currentStart = start;
            
            // TERTIARY coverage
            if (currentStart.isBefore(tertiaryRange.end) && end.isAfter(tertiaryRange.start)) {
                LocalDateTime segmentStart = maxDate(currentStart, tertiaryRange.start);
                LocalDateTime segmentEnd = minDate(end, tertiaryRange.end);
                
                if (!segmentStart.isAfter(segmentEnd)) {
                    coverages.add(new HostCoverage(EsHostEnum.TERTIARY, segmentStart, segmentEnd));
                    currentStart = segmentEnd.plusSeconds(1);
                }
            }
            
            // SECONDARY coverage
            if (currentStart.isBefore(secondaryRange.end) && end.isAfter(secondaryRange.start)) {
                LocalDateTime segmentStart = maxDate(currentStart, secondaryRange.start);
                LocalDateTime segmentEnd = minDate(end, secondaryRange.end);
                
                if (!segmentStart.isAfter(segmentEnd)) {
                    coverages.add(new HostCoverage(EsHostEnum.SECONDARY, segmentStart, segmentEnd));
                    currentStart = segmentEnd.plusSeconds(1);
                }
            }
            
            // PRIMARY coverage
            if (currentStart.isBefore(primaryRange.end) && end.isAfter(primaryRange.start)) {
                LocalDateTime segmentStart = maxDate(currentStart, primaryRange.start);
                LocalDateTime segmentEnd = minDate(end, primaryRange.end);
                
                if (!segmentStart.isAfter(segmentEnd)) {
                    coverages.add(new HostCoverage(EsHostEnum.PRIMARY, segmentStart, segmentEnd));
                }
            }
            
            // Return the list of host coverages
            return coverages;
            
        } catch (Exception e) {
            throw new RuntimeException("Host selection failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the maximum of two LocalDateTime objects
     */
    private LocalDateTime maxDate(LocalDateTime date1, LocalDateTime date2) {
        return date1.isAfter(date2) ? date1 : date2;
    }
    
    /**
     * Get the minimum of two LocalDateTime objects
     */
    private LocalDateTime minDate(LocalDateTime date1, LocalDateTime date2) {
        return date1.isBefore(date2) ? date1 : date2;
    }
}
