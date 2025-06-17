package com.bungakukensaku.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI implementation of the EmbeddingService.
 * Uses OpenAI's text-embedding-3-small model for generating embeddings.
 */
@Service
public class OpenAIEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAIEmbeddingService.class);
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";
    private static final String MODEL = "text-embedding-3-small";
    private static final int EMBEDDING_DIMENSION = 1536;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${openai.api.key:}")
    private String apiKey;
    
    public OpenAIEmbeddingService() {
        this.webClient = WebClient.builder()
            .baseUrl(OPENAI_API_URL)
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(5 * 1024 * 1024)) // 5MB buffer size
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public float[] generateEmbedding(String text) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("not-set-please-configure")) {
            logger.error("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable");
            throw new IllegalStateException("OpenAI API key not configured");
        }
        
        // Log partial key for debugging (first 10 chars only)
        logger.info("Using API key starting with: {}", apiKey.substring(0, Math.min(10, apiKey.length())) + "...");
        
        List<float[]> results = generateEmbeddings(List.of(text));
        return results.get(0);
    }
    
    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (apiKey == null || apiKey.isEmpty()) {
            logger.error("OpenAI API key is not configured. Please set openai.api.key in application.properties");
            throw new IllegalStateException("OpenAI API key not configured");
        }
        
        logger.info("Generating embeddings for {} texts", texts.size());
        
        try {
            // Build request body
            Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "input", texts
            );
            
            // Make API call with retry for rate limits
            String response = webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .retry(3) // Retry up to 3 times
                .block();
            
            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
            
            // Extract embeddings in order
            List<float[]> embeddings = new ArrayList<>();
            for (Map<String, Object> item : data) {
                List<Double> embedding = (List<Double>) item.get("embedding");
                float[] floatArray = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    floatArray[i] = embedding.get(i).floatValue();
                }
                embeddings.add(floatArray);
            }
            
            logger.info("Successfully generated {} embeddings", embeddings.size());
            return embeddings;
            
        } catch (Exception e) {
            logger.error("Error generating embeddings: {}", e.getMessage());
            throw new RuntimeException("Failed to generate embeddings", e);
        }
    }
    
    @Override
    public int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }
}