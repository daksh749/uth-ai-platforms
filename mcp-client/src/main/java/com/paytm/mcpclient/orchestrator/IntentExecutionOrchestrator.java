package com.paytm.mcpclient.orchestrator;

import com.paytm.mcpclient.intent.IntentAnalysisResult;
import com.paytm.mcpclient.intent.UserIntent;
import com.paytm.mcpclient.strategy.IntentExecutionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrator service that routes user intents to appropriate execution strategies.
 * This service acts as the central coordinator for the intent-driven architecture.
 * 
 * Flow:
 * 1. Receives IntentAnalysisResult from IntentAnalyzerService
 * 2. Selects appropriate strategy based on detected intent
 * 3. Delegates execution to the selected strategy
 * 4. Returns the strategy's result
 */
@Service
@Slf4j
public class IntentExecutionOrchestrator {

    private final ApplicationContext applicationContext;
    private final Map<UserIntent, IntentExecutionStrategy> strategyMap = new HashMap<>();

    public IntentExecutionOrchestrator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void initializeStrategies() {
        log.info("Initializing IntentExecutionOrchestrator: Discovering available strategies");
        
        // Discover all IntentExecutionStrategy beans from Spring context
        Map<String, IntentExecutionStrategy> strategies = applicationContext.getBeansOfType(IntentExecutionStrategy.class);
        
        for (IntentExecutionStrategy strategy : strategies.values()) {
            UserIntent supportedIntent = strategy.getSupportedIntent();
            strategyMap.put(supportedIntent, strategy);
            log.info("Registered strategy: {} for intent: {}", 
                strategy.getClass().getSimpleName(), supportedIntent);
        }
        
        log.info("IntentExecutionOrchestrator initialized with {} strategies", strategyMap.size());
        
        // Log all available strategies for debugging
        strategyMap.forEach((intent, strategy) -> 
            log.debug("Intent {} → Strategy: {} ({})", 
                intent, strategy.getClass().getSimpleName(), strategy.getDescription()));
    }

    /**
     * Execute the user's request based on the analyzed intent.
     * This is the main entry point for the orchestrator.
     * 
     * @param userPrompt The original user prompt/query
     * @param analysisResult The result from intent analysis
     * @return The execution result from the selected strategy
     */
    public Object executeIntent(String userPrompt, IntentAnalysisResult analysisResult) {
        log.info("Orchestrating execution for intent: {} (confidence: {:.2f})", 
            analysisResult.getIntent(), analysisResult.getConfidence());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate analysis result
            if (!analysisResult.isSuccess()) {
                log.error("Cannot execute - intent analysis failed: {}", analysisResult.getError());
                return buildErrorResponse("Intent analysis failed", analysisResult.getError(), 0);
            }
            
            // Get the appropriate strategy
            IntentExecutionStrategy strategy = getStrategy(analysisResult.getIntent());
            if (strategy == null) {
                String error = String.format("No strategy found for intent: %s", analysisResult.getIntent());
                log.error(error);
                return buildErrorResponse("Strategy not found", error, System.currentTimeMillis() - startTime);
            }
            
            // Validate strategy can execute
            if (!strategy.canExecute(analysisResult)) {
                String error = String.format("Strategy %s cannot execute for analysis result", 
                    strategy.getClass().getSimpleName());
                log.error(error);
                return buildErrorResponse("Strategy validation failed", error, System.currentTimeMillis() - startTime);
            }
            
            log.info("Executing strategy: {} for intent: {}", 
                strategy.getClass().getSimpleName(), analysisResult.getIntent());
            
            // Execute the strategy
            Object result = strategy.execute(userPrompt, analysisResult);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Intent execution completed in {}ms using strategy: {}", 
                executionTime, strategy.getClass().getSimpleName());
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Intent execution failed after {}ms for intent: {}", 
                executionTime, analysisResult.getIntent(), e);
            
            return buildErrorResponse("Execution failed", e.getMessage(), executionTime);
        }
    }

    /**
     * Get the strategy for a specific intent
     */
    private IntentExecutionStrategy getStrategy(UserIntent intent) {
        return strategyMap.get(intent);
    }

    /**
     * Check if a strategy exists for the given intent
     */
    public boolean hasStrategy(UserIntent intent) {
        return strategyMap.containsKey(intent);
    }

    /**
     * Get all supported intents
     */
    public UserIntent[] getSupportedIntents() {
        return strategyMap.keySet().toArray(new UserIntent[0]);
    }

    private Map<String, Object> buildErrorResponse(String errorType, String message, long executionTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("errorType", errorType);
        response.put("message", message);
        response.put("executionTime", executionTime + "ms");
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }
}
