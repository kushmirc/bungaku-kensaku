package com.bungakukensaku.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bungakukensaku.dto.SearchResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for generating AI-powered context-aware summaries for search results.
 * Implements the "digital study group leader" concept by providing intelligent
 * explanations of why passages are relevant and their context.
 */
@Service
public class AISummaryService {
    
    private static final Logger logger = LoggerFactory.getLogger(AISummaryService.class);
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-3.5-turbo"; // Start with GPT-3.5 for cost efficiency
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${openai.api.key:}")
    private String apiKey;
    
    @Value("${ai.service.type:java}")
    private String aiServiceType;
    
    @Value("${ai.service.python.url:http://localhost:8001}")
    private String pythonServiceUrl;
    
    public AISummaryService() {
        this.webClient = WebClient.builder()
            .baseUrl(OPENAI_API_URL)
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(5 * 1024 * 1024)) // 5MB buffer size
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Generate a context-aware summary for a search result.
     * 
     * @param result The search result item
     * @param query The user's search query
     * @return A summary with context and relevance explanation
     */
    public SearchResultSummary generateSummary(SearchResultItem result, String query) {
        logger.info("Generating summary for chunk {} from book: {} using {} service", 
            result.getChunkId(), result.getBookTitle(), aiServiceType);
        
        try {
            if ("python".equals(aiServiceType)) {
                return generateSummaryViaPython(result, query);
            } else {
                return generateSummaryViaJava(result, query);
            }
        } catch (Exception e) {
            logger.error("Error generating summary: {}", e.getMessage());
            return new SearchResultSummary("", "");
        }
    }
    
    private SearchResultSummary generateSummaryViaPython(SearchResultItem result, String query) {
        try {
            // Build request body for Python service
            Map<String, Object> requestBody = Map.of(
                "chunk", result.getContent(),
                "query", query,
                "bookTitle", result.getBookTitle(),
                "chapterTitle", result.getChapter() != null ? result.getChapter() : ""
            );
            
            // Create a new WebClient for Python service
            WebClient pythonClient = WebClient.builder()
                .baseUrl(pythonServiceUrl)
                .codecs(configurer -> configurer
                    .defaultCodecs()
                    .maxInMemorySize(5 * 1024 * 1024))
                .build();
            
            // Call Python service
            String response = pythonClient.post()
                .uri("/generate-summary")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            String contextSummary = (String) responseMap.get("contextSummary");
            String relevanceExplanation = (String) responseMap.get("relevanceExplanation");
            
            return new SearchResultSummary(
                contextSummary != null ? contextSummary : "",
                relevanceExplanation != null ? relevanceExplanation : ""
            );
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Python service response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Python service response", e);
        } catch (Exception e) {
            logger.error("Error calling Python service: {}", e.getMessage());
            throw new RuntimeException("Failed to call Python service", e);
        }
    }
    
    private SearchResultSummary generateSummaryViaJava(SearchResultItem result, String query) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("not-set-please-configure")) {
            logger.error("OpenAI API key is not configured");
            return new SearchResultSummary("", "");
        }
        
        try {
            String systemPrompt = createSystemPrompt();
            String userPrompt = createUserPrompt(result, query);
            
            // Build request
            Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.7,
                "max_tokens", 300
            );
            
            // Make API call
            String response = webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");
            
            // Parse the structured response
            return parseStructuredResponse(content);
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing OpenAI response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse OpenAI response", e);
        } catch (Exception e) {
            logger.error("Error generating summary via Java: {}", e.getMessage());
            throw new RuntimeException("Failed to generate summary via Java", e);
        }
    }
    
    /**
     * Generate summaries for multiple search results in batch.
     * More efficient for multiple results.
     */
    public List<SearchResultSummary> generateSummaries(List<SearchResultItem> results, String query) {
        // For now, generate individually. Can optimize with batch API later.
        List<SearchResultSummary> summaries = new ArrayList<>();
        for (SearchResultItem result : results) {
            summaries.add(generateSummary(result, query));
        }
        return summaries;
    }
    
    private String createSystemPrompt() {
        return """
            あなたは仏法哲学、特に池田大作先生の著作に精通した座談会のリーダーです。
            読者が本の文章の文脈と関連性を理解できるよう支援することがあなたの役割です。
            
            文章と検索クエリが与えられたら、以下を提供してください：
            1. この文章で何が起きているかを説明する簡潔な文脈の要約
            2. なぜこの文章が検索クエリと関連しているかの説明
            
            回答は簡潔で役立つものにしてください。教えを読者にとってアクセスしやすく、
            意味のあるものにすることに焦点を当ててください。温かく、支援的なトーンで書いてください。
            
            必ず日本語で回答してください。
            
            回答は以下の形式で：
            文脈: [文章で何が起きているかの簡潔な説明]
            関連性: [なぜこの文章が検索クエリと関連しているか]
            """;
    }
    
    private String createUserPrompt(SearchResultItem result, String query) {
        return String.format("""
            書籍: %s
            章: %s
            検索クエリ: %s
            
            文章:
            %s
            
            この文章の文脈の要約と関連性の説明を提供してください。
            """, 
            result.getBookTitle(),
            result.getChapter() != null ? result.getChapter() : "不明",
            query,
            result.getContent()
        );
    }
    
    private SearchResultSummary parseStructuredResponse(String response) {
        try {
            String[] lines = response.split("\n");
            String context = "";
            String relevance = "";
            
            for (String line : lines) {
                if (line.startsWith("文脈:")) {
                    context = line.substring("文脈:".length()).trim();
                } else if (line.startsWith("関連性:")) {
                    relevance = line.substring("関連性:".length()).trim();
                }
            }
            
            return new SearchResultSummary(context, relevance);
        } catch (Exception e) {
            logger.error("Error parsing structured response: {}", e.getMessage());
            return new SearchResultSummary(response, "");
        }
    }
    
    /**
     * Extract the most relevant contiguous portion of a chunk based on the search query.
     * This helps present more focused and relevant excerpts to users.
     * 
     * @param fullContent The complete chunk content
     * @param query The user's search query
     * @return The most relevant excerpt from the chunk
     */
    public String extractRelevantExcerpt(String fullContent, String query) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("not-set-please-configure")) {
            logger.error("OpenAI API key is not configured");
            return fullContent; // Return full content as fallback
        }
        
        try {
            String systemPrompt = """
                You are an expert at identifying the most relevant portions of text based on search queries.
                
                Your task is to extract the most relevant CONTIGUOUS portion of the given text that best 
                addresses the search query. The excerpt must be:
                1. A continuous section from the original text (not pieced together from different parts)
                2. Include complete sentences (don't cut off mid-sentence)
                3. Between 30% and 100% of the original text length
                4. The portion that best helps the reader understand the connection to their search
                
                Return ONLY the extracted text, without any additional commentary or formatting.
                If the entire text is relevant, return the entire text.
                """;
            
            String userPrompt = String.format("""
                Search Query: %s
                
                Full Text:
                %s
                
                Extract the most relevant contiguous portion that addresses this search query.
                """, query, fullContent);
            
            // Build request
            Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.3, // Lower temperature for more consistent extraction
                "max_tokens", 1000 // Allow for longer excerpts
            );
            
            // Make API call
            String response = webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String excerpt = (String) message.get("content");
            
            // Validate that the excerpt is actually from the original text
            // (AI might occasionally paraphrase, which we want to avoid)
            if (fullContent.contains(excerpt.trim())) {
                return excerpt.trim();
            } else {
                // If AI returned something not in the original, fall back to full content
                logger.warn("AI returned excerpt not found in original text, using full content");
                return fullContent;
            }
            
        } catch (Exception e) {
            logger.error("Error extracting relevant excerpt: {}", e.getMessage());
            return fullContent; // Return full content as fallback
        }
    }
    
    /**
     * Inner class to hold summary and relevance information
     */
    public static class SearchResultSummary {
        private final String contextSummary;
        private final String relevanceExplanation;
        
        public SearchResultSummary(String contextSummary, String relevanceExplanation) {
            this.contextSummary = contextSummary;
            this.relevanceExplanation = relevanceExplanation;
        }
        
        public String getContextSummary() {
            return contextSummary;
        }
        
        public String getRelevanceExplanation() {
            return relevanceExplanation;
        }
    }
}