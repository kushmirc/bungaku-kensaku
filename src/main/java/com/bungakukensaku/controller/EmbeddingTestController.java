package com.bungakukensaku.controller;

import com.bungakukensaku.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for verifying embedding generation.
 * This is temporary and should be removed in production.
 */
@RestController
@RequestMapping("/api/test")
public class EmbeddingTestController {
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @PostMapping("/embedding")
    public Map<String, Object> testEmbedding(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        
        if (text == null || text.isEmpty()) {
            text = "青年時代は、人生の土台を築く最も重要な時期です。";
        }
        
        try {
            // Generate embedding
            float[] embedding = embeddingService.generateEmbedding(text);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("text", text);
            response.put("dimension", embedding.length);
            response.put("embedding_preview", Arrays.toString(Arrays.copyOf(embedding, 10)) + "...");
            response.put("success", true);
            
            return response;
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
}