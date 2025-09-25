package com.paytm.mcpclient.strategy;

import com.paytm.mcpclient.intent.IntentAnalysisResult;
import com.paytm.mcpclient.intent.UserIntent;

/**
 * Strategy interface for executing different types of user intents.
 * Each implementation handles a specific intent type (SCHEMA_ONLY, HOST_ONLY, COMPLEX_SEARCH).
 * 
 * This follows the Strategy Pattern to encapsulate different execution algorithms
 * based on the classified user intent.
 */
public interface IntentExecutionStrategy {
    
    /**
     * Get the intent type that this strategy handles.
     * 
     * @return The UserIntent that this strategy is designed to execute
     */
    UserIntent getSupportedIntent();
    
    /**
     * Execute the strategy for the given user prompt and analysis result.
     * 
     * @param userPrompt The original user prompt/query
     * @param analysisResult The result from intent analysis containing intent, confidence, etc.
     * @return The execution result (format depends on the specific strategy)
     * @throws Exception If execution fails
     */
    Object execute(String userPrompt, IntentAnalysisResult analysisResult) throws Exception;
    
    /**
     * Check if this strategy can handle the given intent.
     * 
     * @param intent The intent to check
     * @return true if this strategy can handle the intent, false otherwise
     */
    default boolean canHandle(UserIntent intent) {
        return getSupportedIntent() == intent;
    }
    
    /**
     * Get a description of what this strategy does.
     * Used for logging and debugging purposes.
     * 
     * @return A human-readable description of the strategy
     */
    default String getDescription() {
        return String.format("Strategy for handling %s intent", getSupportedIntent());
    }
    
    /**
     * Validate if the strategy can execute with the given analysis result.
     * This allows for pre-execution validation.
     * 
     * @param analysisResult The analysis result to validate
     * @return true if the strategy can execute, false otherwise
     */
    default boolean canExecute(IntentAnalysisResult analysisResult) {
        return analysisResult != null 
            && analysisResult.isSuccess() 
            && canHandle(analysisResult.getIntent());
    }
    
    /**
     * Get the estimated execution complexity for this strategy.
     * Used for performance monitoring and resource allocation.
     * 
     * @return Complexity level (1=simple, 2=moderate, 3=complex)
     */
    default int getExecutionComplexity() {
        return getSupportedIntent().getComplexityLevel();
    }
}
