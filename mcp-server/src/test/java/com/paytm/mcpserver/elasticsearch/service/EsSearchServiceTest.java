package com.paytm.mcpserver.elasticsearch.service;

import com.paytm.shared.elasticsearch.model.EsHostType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class demonstrating ES Search Tool usage
 * Note: These are integration tests that require a running Elasticsearch cluster
 */
public class EsSearchServiceTest {
    
    // Note: Uncomment and run these tests when you have ES running
    
    /*
    @Autowired
    private EsSearchService esSearchService;
    
    @Test
    public void testSimpleSearch() {
        // Create a simple search query
        SearchSourceBuilder searchQuery = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("searchFields.searchRemarks", "Blinkit"))
            .size(10)
            .sort("txnDate", SortOrder.DESC);
        
        // Execute search on primary host with specific indices
        Object result = esSearchService.executeSearch(
            searchQuery,
            EsHostType.PRIMARY,
            Arrays.asList("payment-history-2024-01")
        );
        
        // Validate response
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        
        assertEquals("success", response.get("status"));
        assertNotNull(response.get("hits"));
        assertNotNull(response.get("query_info"));
        
        System.out.println("Search Result: " + response);
    }
    
    @Test
    public void testSearchAllIndices() {
        // Create a range query for amount
        SearchSourceBuilder searchQuery = new SearchSourceBuilder()
            .query(QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("amount").gte(1000).lte(50000))
                .filter(QueryBuilders.termQuery("status", "2"))
            )
            .size(20)
            .sort("txnDate", SortOrder.DESC);
        
        // Execute search on all payment-history indices (indices = null)
        Object result = esSearchService.executeSearch(
            searchQuery,
            EsHostType.PRIMARY,
            null  // Will search all payment-history-* indices
        );
        
        // Validate response
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        
        assertEquals("success", response.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) response.get("hits");
        assertTrue((Long) hits.get("total") >= 0);
        
        System.out.println("Range Search Result: " + response);
    }
    
    @Test
    public void testComplexQuery() {
        // Create a complex bool query with aggregations
        SearchSourceBuilder searchQuery = new SearchSourceBuilder()
            .query(QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("searchFields.searchOtherName", "merchant"))
                .filter(QueryBuilders.rangeQuery("txnDate").gte("2024-01-01").lte("2024-12-31"))
                .filter(QueryBuilders.termQuery("txnIndicator", "2"))
            )
            .size(50)
            .sort("amount", SortOrder.DESC)
            .sort("txnDate", SortOrder.DESC);
        
        // Execute on secondary host
        Object result = esSearchService.executeSearch(
            searchQuery,
            EsHostType.SECONDARY,
            Arrays.asList("payment-history-2024-01", "payment-history-2024-02")
        );
        
        // Validate response
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        
        assertEquals("success", response.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> queryInfo = (Map<String, Object>) response.get("query_info");
        assertEquals("SECONDARY", queryInfo.get("host_type"));
        
        System.out.println("Complex Query Result: " + response);
    }
    */
    
    @Test
    public void testToolInterface() {
        // This test demonstrates the tool interface without requiring ES
        EsSearchService tool = new EsSearchService();
        
        // Test tool metadata
        assertEquals("es_search", tool.getName());
        assertEquals(Arrays.asList("searchSourceBuilder", "esHost"), tool.getRequiredParameters());
        assertEquals(Arrays.asList("indices"), tool.getOptionalParameters());
        
        System.out.println("Tool Name: " + tool.getName());
        System.out.println("Required Parameters: " + tool.getRequiredParameters());
        System.out.println("Optional Parameters: " + tool.getOptionalParameters());
    }
}
