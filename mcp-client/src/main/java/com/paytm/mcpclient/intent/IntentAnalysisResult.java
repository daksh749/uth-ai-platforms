package com.paytm.mcpclient.intent;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Represents the result of the LLM-based intent analysis.
 * Contains the detected intent, confidence score, and metadata.
 */
@Data
@Builder
public class IntentAnalysisResult {
    
    /**
     * The classified intent of the user's request.
     */
    private UserIntent intent;
    
    /**
     * Confidence score of the intent detection (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * Parameters extracted from the user prompt (deprecated - now handled by strategies)
     * @deprecated Parameters are now extracted by individual strategies when needed
     */
    @Deprecated
    private Map<String, Object> extractedParams;
    
    /**
     * Additional metadata about the analysis (reasoning, context, etc.)
     */
    private String analysisMetadata;
    
    /**
     * Any error message if analysis failed
     */
    private String error;
    
    /**
     * Whether the analysis was successful
     */
    private boolean success;
    
    /**
     * Create a successful analysis result (simplified - no parameter extraction)
     */
    public static IntentAnalysisResult success(UserIntent intent, double confidence, String reasoning) {
        return IntentAnalysisResult.builder()
            .intent(intent)
            .confidence(confidence)
            .analysisMetadata(reasoning)
            .success(true)
            .build();
    }
    
    /**
     * Create a successful analysis result (legacy - with params)
     * @deprecated Use success(intent, confidence, reasoning) instead
     */
    @Deprecated
    public static IntentAnalysisResult success(UserIntent intent, double confidence, Map<String, Object> params) {
        return IntentAnalysisResult.builder()
            .intent(intent)
            .confidence(confidence)
            .extractedParams(params)
            .success(true)
            .build();
    }
    
    /**
     * Create a failed analysis result
     */
    public static IntentAnalysisResult failure(String error) {
        return IntentAnalysisResult.builder()
            .success(false)
            .error(error)
            .intent(UserIntent.COMPLEX_SEARCH)
            .confidence(0.0)
            .build();
    }
    
    /**
     * Check if the confidence is above threshold
     */
    public boolean isConfident(double threshold) {
        return success && confidence >= threshold;
    }
    
    /**
     * Check if the analysis was successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Get a summary for logging
     */
    public String getSummary() {
        if (!success) {
            return String.format("FAILED: %s", error);
        }
        return String.format("%s (%.2f confidence)", intent, confidence);
    }
    
    /**
     * Get detailed information for debugging
     */
    public String getDetailedInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Intent Analysis Result:\n");
        sb.append(String.format("  Success: %s\n", success));
        sb.append(String.format("  Intent: %s\n", intent));
        sb.append(String.format("  Confidence: %.3f\n", confidence));
        if (analysisMetadata != null) {
            sb.append(String.format("  Reasoning: %s\n", analysisMetadata));
        }
        if (error != null) {
            sb.append(String.format("  Error: %s\n", error));
        }
        return sb.toString();
    }
}
