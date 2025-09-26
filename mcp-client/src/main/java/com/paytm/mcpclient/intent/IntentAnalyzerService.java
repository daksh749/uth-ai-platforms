package com.paytm.mcpclient.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for analyzing user intent using an LLM and predefined rules.
 * This service classifies user prompts into specific intents (e.g., SCHEMA_ONLY, HOST_ONLY, COMPLEX_SEARCH).
 */
@Service
@Slf4j
public class IntentAnalyzerService {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String INTENT_RULES_FILE = "rules/intent-analysis-rules.json";
    // Default confidence threshold for intent classification
    @SuppressWarnings("unused")
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.7;

    private Map<String, Object> cachedRules;

    public IntentAnalyzerService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing IntentAnalyzerService: Loading intent analysis rules from {}", INTENT_RULES_FILE);
        getIntentRules(); // Load rules on startup
    }

    /**
     * Analyzes the user's prompt to determine the intent.
     *
     * @param userPrompt The user's input prompt.
     * @return An IntentAnalysisResult containing the classified intent and confidence.
     */
    public IntentAnalysisResult analyzeIntent(String userPrompt) {
        log.info("Analyzing intent for user prompt: {}", userPrompt);
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> rulesMap = getIntentRules();
            if (rulesMap.isEmpty()) {
                return IntentAnalysisResult.failure("Intent analysis rules not loaded.");
            }

            String rulesJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rulesMap.get("classification_config"));
            
            String llmPrompt = buildIntentAnalysisPrompt(userPrompt, rulesJson);
            String llmResponse = chatLanguageModel.generate(llmPrompt);

            IntentAnalysisResult result = parseLlmResponse(llmResponse);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Intent analysis completed in {}ms. Intent: {}, Confidence: {}",
                executionTime, result.getIntent(), result.getConfidence());

            return result;

        } catch (Exception e) {
            @SuppressWarnings("unused")
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Error during intent analysis for prompt: {}", userPrompt, e);
            return IntentAnalysisResult.failure("Intent analysis failed: " + e.getMessage());
        }
    }

    /**
     * Builds the LLM prompt for intent analysis
     */
    private String buildIntentAnalysisPrompt(String userPrompt, String rulesJson) {
        return String.format("""
            You are an expert intent classifier for Elasticsearch queries.

            USER REQUEST: "%s"

            CLASSIFICATION RULES:
            %s

            INSTRUCTIONS:
            1. Analyze the user request carefully
            2. Apply the classification rules to determine the appropriate intent
            3. Provide confidence score between 0.0 and 1.0
            4. Include reasoning for your classification

            MANDATORY RESPONSE FORMAT (JSON only):
            {
              "intent": "DETERMINED_INTENT",
              "confidence": 0.XX,
              "reasoning": "Your reasoning here"
            }

            RESPOND WITH VALID JSON ONLY - NO OTHER TEXT!
            """, userPrompt, rulesJson);
    }

    /**
     * Parses the LLM's JSON response into an IntentAnalysisResult object.
     * Expected format: {"intent": "...", "confidence": ..., "reasoning": "..."}
     */
    private IntentAnalysisResult parseLlmResponse(String llmResponse) {
        try {
            // Clean the response to ensure it's valid JSON
            String cleanedResponse = cleanLlmResponse(llmResponse);
            JsonNode responseNode = objectMapper.readTree(cleanedResponse);

            // Extract intent - default to COMPLEX_SEARCH if unknown
            UserIntent intent = UserIntent.COMPLEX_SEARCH;
            if (responseNode.has("intent")) {
                try {
                    intent = UserIntent.valueOf(responseNode.get("intent").asText().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown intent received from LLM: {}, defaulting to COMPLEX_SEARCH", responseNode.get("intent").asText());
                    intent = UserIntent.COMPLEX_SEARCH;
                }
            }

            // Extract confidence
            double confidence = responseNode.has("confidence") ?
                responseNode.get("confidence").asDouble() : 0.0;

            // Extract reasoning (optional)
            String reasoning = responseNode.has("reasoning") ?
                responseNode.get("reasoning").asText() : null;

            return IntentAnalysisResult.success(intent, confidence, reasoning);

        } catch (Exception e) {
            log.error("Failed to parse LLM intent response: {}", llmResponse, e);
            return IntentAnalysisResult.failure("Failed to parse LLM response: " + e.getMessage());
        }
    }

    /**
     * Cleans the LLM response to ensure it's a valid JSON string.
     * Removes markdown code blocks and extracts the JSON part.
     */
    private String cleanLlmResponse(String response) {
        if (response == null) {
            return "{}";
        }

        // Remove markdown code blocks
        String cleaned = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");

        // Attempt to find the first and last brace to ensure it's a complete JSON object
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        return cleaned.trim();
    }

    /**
     * Loads intent analysis rules from a JSON file with caching.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getIntentRules() {
        if (cachedRules != null) {
            return cachedRules;
        }

        try {
            ClassPathResource resource = new ClassPathResource(INTENT_RULES_FILE);

            if (!resource.exists()) {
                log.warn("Intent analysis rules file not found: {}. Using empty rules.", INTENT_RULES_FILE);
                cachedRules = new HashMap<>();
                return cachedRules;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode rulesNode = objectMapper.readTree(inputStream);
                cachedRules = objectMapper.convertValue(rulesNode, Map.class);
                log.info("Successfully loaded intent analysis rules from: {}", INTENT_RULES_FILE);
                return cachedRules;
            }

        } catch (IOException e) {
            log.error("Failed to load intent rules from: {}", INTENT_RULES_FILE, e);
            return new HashMap<>();
        }
    }

    /**
     * Refresh cached rules (for configuration updates)
     */
    public void refreshRules() {
        log.info("Refreshing intent analysis rules cache");
        synchronized (this) {
            cachedRules = null;
            getIntentRules(); // Reload and cache
        }
        log.info("Intent analysis rules cache refreshed successfully");
    }
}
