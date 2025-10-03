package com.paytm.mcpserver.service;

import com.paytm.mcpserver.utility.ElasticsearchUtility;
import com.paytm.mcpserver.utility.DateFormatUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * Service for finding relevant Elasticsearch indices based on date ranges
 *
 * Generates index names following the pattern: payment-history-MM-yyyy*
 * For date ranges spanning multiple months, returns all indices in between.
 */
@Service
public class ElasticSearchIndexFetcher{

    @Value("${elasticsearch.index-patterns.file:classpath:schemas/index-patterns.json}")
    private Resource indexPatternsResource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Find relevant indices based on date range
     * Returns list of index names covering the entire date range
     */
    public List<String> findIndicesForDateRange(String startDate, String endDate) {
        try {
            if(StringUtils.isEmpty(startDate)){
                startDate = ElasticsearchUtility.getDefaultStartDate();
            }
            if(StringUtils.isEmpty(endDate)){
                endDate = ElasticsearchUtility.getDefaultEndDate();
            }
            // Parse dates in DD-MM-YYYY format
            LocalDate start = DateFormatUtility.parseDate(startDate);
            LocalDate end = DateFormatUtility.parseDate(endDate);

            String indexPattern = getIndexPattern();

            return generateIndicesForDateRange(start, end, indexPattern);

        } catch (Exception e) {
            throw new RuntimeException("Failed to find indices for date range: " + e.getMessage(), e);
        }
    }

    /**
     * Get index pattern from JSON configuration file
     */
    private String getIndexPattern() {
        try {
            String jsonContent = indexPatternsResource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            return rootNode.get("index-pattern").asText();
        } catch (Exception e) {
            // Fallback to default pattern if file not found
            return "payment-history-MM-yyyy*";
        }
    }

    /**
     * Generate list of indices for the date range
     * Creates one index per month from start month to end month (inclusive)
     */
    private List<String> generateIndicesForDateRange(LocalDate startDate, LocalDate endDate, String pattern) {
        List<String> indices = new ArrayList<>();

        YearMonth startMonth = YearMonth.from(startDate);
        YearMonth endMonth = YearMonth.from(endDate);

        YearMonth currentMonth = startMonth;

        while (!currentMonth.isAfter(endMonth)) {
            String indexName = formatIndexName(currentMonth, pattern);
            indices.add(indexName);
            currentMonth = currentMonth.plusMonths(1);
        }

        return indices;
    }

    /**
     * Format index name based on pattern and date
     * Replaces MM with zero-padded month and yyyy with year
     */
    private String formatIndexName(YearMonth yearMonth, String pattern) {
        String monthFormatted = String.format("%02d", yearMonth.getMonthValue());
        String yearFormatted = String.valueOf(yearMonth.getYear());

        return pattern
                .replace("MM", monthFormatted)
                .replace("yyyy", yearFormatted);
    }
}
