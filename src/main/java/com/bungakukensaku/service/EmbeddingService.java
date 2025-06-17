package com.bungakukensaku.service;

import java.util.List;

/**
 * Service interface for generating text embeddings.
 * 
 * This interface is designed to be implementation-agnostic,
 * allowing for easy switching between external APIs (OpenAI, HuggingFace)
 * and local model hosting in the future.
 */
public interface EmbeddingService {
    
    /**
     * Generate embeddings for a single text.
     * 
     * @param text The text to generate embeddings for
     * @return Array of float values representing the embedding vector
     */
    float[] generateEmbedding(String text);
    
    /**
     * Generate embeddings for multiple texts in batch.
     * More efficient than calling generateEmbedding multiple times.
     * 
     * @param texts List of texts to generate embeddings for
     * @return List of embedding vectors
     */
    List<float[]> generateEmbeddings(List<String> texts);
    
    /**
     * Get the dimension size of the embeddings.
     * Useful for vector database configuration.
     * 
     * @return The dimension size of embedding vectors
     */
    int getEmbeddingDimension();
}