package com.bungakukensaku.controller;

import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Chunk;
import com.bungakukensaku.repository.BookRepository;
import com.bungakukensaku.repository.ChunkRepository;
import com.bungakukensaku.service.DocumentProcessingService;
import com.bungakukensaku.service.EmbeddingService;
import com.bungakukensaku.service.PineconeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time migration controller to re-process books with fixed EPUB extraction
 */
@RestController
@RequestMapping("/api/migration")
public class MigrationController {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MigrationController.class);
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private PineconeService pineconeService;
    
    @org.springframework.beans.factory.annotation.Value("${pinecone.index-name}")
    private String pineconeIndexName;
    
    /**
     * Re-process a single book by uploading its EPUB file again
     */
    @PostMapping("/reprocess-book/{bookId}")
    @Transactional
    public Map<String, Object> reprocessBook(
            @PathVariable Long bookId,
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Find the book
            Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found: " + bookId));
            
            result.put("bookTitle", book.getTitle());
            result.put("oldChunkCount", chunkRepository.countByBookId(bookId));
            
            // Delete old chunks
            List<Chunk> oldChunks = chunkRepository.findByBookId(bookId);
            chunkRepository.deleteAll(oldChunks);
            result.put("deletedChunks", oldChunks.size());
            
            // Save uploaded file temporarily
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            Path tempFile = Files.createTempFile("reprocess-", extension);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Process with fixed extraction
            List<Chunk> newChunks = documentProcessingService.processDocument(
                tempFile.toString(), book
            );
            
            // Save new chunks
            newChunks = chunkRepository.saveAll(newChunks);
            result.put("newChunkCount", newChunks.size());
            
            // Calculate total characters
            long totalChars = newChunks.stream()
                .mapToLong(chunk -> chunk.getContent().length())
                .sum();
            result.put("totalCharacters", totalChars);
            result.put("estimatedPages", totalChars / 1000);
            
            // Clean up
            Files.deleteIfExists(tempFile);
            
            result.put("success", true);
            result.put("message", "Successfully reprocessed book with full content extraction");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Get summary of what needs to be reprocessed
     */
    @GetMapping("/status")
    public Map<String, Object> getMigrationStatus() {
        Map<String, Object> status = new HashMap<>();
        
        List<Book> books = bookRepository.findAll();
        status.put("totalBooks", books.size());
        
        // Create a list of book info objects instead of individual keys
        List<Map<String, Object>> bookList = new ArrayList<>();
        
        for (Book book : books) {
            Map<String, Object> bookInfo = new HashMap<>();
            bookInfo.put("title", book.getTitle());
            bookInfo.put("id", book.getId());
            bookInfo.put("author", book.getAuthor());
            bookInfo.put("series", book.getSeries());
            
            Long chunkCount = chunkRepository.countByBookId(book.getId());
            bookInfo.put("currentChunks", chunkCount);
            
            // Estimate if it needs reprocessing (less than 60 chunks probably means truncated)
            bookInfo.put("needsReprocessing", chunkCount < 60);
            
            bookList.add(bookInfo);
        }
        
        // Sort books alphabetically by title
        bookList.sort((a, b) -> ((String) a.get("title")).compareToIgnoreCase((String) b.get("title")));
        
        status.put("books", bookList);
        
        return status;
    }
    
    /**
     * Generate embeddings for all chunks that don't have them yet
     */
    @PostMapping("/generate-embeddings")
    @Transactional
    public Map<String, Object> generateEmbeddings() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get all chunks that need embeddings
            List<Chunk> chunks = chunkRepository.findAll();
            List<Chunk> chunksNeedingEmbeddings = new ArrayList<>();
            
            for (Chunk chunk : chunks) {
                if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                    chunksNeedingEmbeddings.add(chunk);
                }
            }
            
            result.put("totalChunks", chunks.size());
            result.put("chunksNeedingEmbeddings", chunksNeedingEmbeddings.size());
            
            if (chunksNeedingEmbeddings.isEmpty()) {
                result.put("message", "All chunks already have embeddings");
                result.put("success", true);
                return result;
            }
            
            // Process in batches of 50 to avoid API limits
            int batchSize = 50;
            int processed = 0;
            
            for (int i = 0; i < chunksNeedingEmbeddings.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunksNeedingEmbeddings.size());
                List<Chunk> batch = chunksNeedingEmbeddings.subList(i, end);
                
                // Extract texts
                List<String> texts = new ArrayList<>();
                for (Chunk chunk : batch) {
                    texts.add(chunk.getContent());
                }
                
                // Generate embeddings
                List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
                
                // Update chunks with embeddings
                for (int j = 0; j < batch.size(); j++) {
                    batch.get(j).setEmbedding(embeddings.get(j));
                }
                
                // Save batch
                chunkRepository.saveAll(batch);
                processed += batch.size();
                
                result.put("processedSoFar", processed);
            }
            
            result.put("success", true);
            result.put("message", "Successfully generated embeddings for " + processed + " chunks");
            result.put("totalProcessed", processed);
            
            // Estimate cost (OpenAI text-embedding-3-small is $0.02 per 1M tokens)
            // Rough estimate: 500 tokens per chunk
            double estimatedCost = (processed * 500 / 1_000_000.0) * 0.02;
            result.put("estimatedCost", "$" + String.format("%.4f", estimatedCost));
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Upload new embeddings to Pinecone vector database (incremental)
     */
    @PostMapping("/upload-new-to-pinecone")
    @Transactional
    public Map<String, Object> uploadNewToPinecone() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get chunks with embeddings that haven't been uploaded yet
            List<Chunk> chunks = chunkRepository.findAll();
            List<Chunk> chunksToUpload = new ArrayList<>();
            
            for (Chunk chunk : chunks) {
                if (chunk.getEmbedding() != null && chunk.getEmbedding().length > 0 && !chunk.isUploadedToPinecone()) {
                    chunksToUpload.add(chunk);
                }
            }
            
            result.put("totalChunks", chunks.size());
            result.put("chunksToUpload", chunksToUpload.size());
            
            if (chunksToUpload.isEmpty()) {
                result.put("message", "No new chunks to upload. All chunks with embeddings are already in Pinecone.");
                result.put("success", true);
                return result;
            }
            
            // Skip getting index stats before upload to avoid serialization issues
            // The stats contain protobuf objects that can't be serialized to JSON
            result.put("indexStatsBefore", null);
            
            // Process in batches of 100 for Pinecone
            int batchSize = 100;
            int uploaded = 0;
            
            List<PineconeService.VectorData> vectors = new ArrayList<>();
            
            for (Chunk chunk : chunksToUpload) {
                // Convert float[] to List<Float>
                List<Float> values = new ArrayList<>();
                for (float val : chunk.getEmbedding()) {
                    values.add(val);
                }
                
                // Create metadata (handle null values)
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("chunkId", chunk.getId());
                metadata.put("bookId", chunk.getBook().getId());
                metadata.put("bookTitle", chunk.getBook().getTitle());
                if (chunk.getChapter() != null) {
                    metadata.put("chapter", chunk.getChapter());
                }
                if (chunk.getPageNum() != null) {
                    metadata.put("pageNum", chunk.getPageNum());
                }
                metadata.put("content", chunk.getContent());
                
                // Create vector data
                PineconeService.VectorData vector = new PineconeService.VectorData(
                    "chunk-" + chunk.getId(),
                    values,
                    metadata
                );
                vectors.add(vector);
                
                // Upload batch when we reach the batch size
                if (vectors.size() >= batchSize) {
                    pineconeService.upsertVectors(vectors);
                    
                    // Mark chunks as uploaded
                    for (int i = uploaded; i < uploaded + vectors.size(); i++) {
                        chunksToUpload.get(i).setUploadedToPinecone(true);
                    }
                    chunkRepository.saveAll(chunksToUpload.subList(uploaded, uploaded + vectors.size()));
                    
                    uploaded += vectors.size();
                    vectors.clear();
                    result.put("uploadedSoFar", uploaded);
                }
            }
            
            // Upload remaining vectors
            if (!vectors.isEmpty()) {
                pineconeService.upsertVectors(vectors);
                
                // Mark remaining chunks as uploaded
                for (int i = uploaded; i < chunksToUpload.size(); i++) {
                    chunksToUpload.get(i).setUploadedToPinecone(true);
                }
                chunkRepository.saveAll(chunksToUpload.subList(uploaded, chunksToUpload.size()));
                
                uploaded += vectors.size();
            }
            
            // Skip getting index stats after upload to avoid serialization issues
            // The stats contain protobuf objects that can't be serialized to JSON
            result.put("indexStatsAfter", null);
            
            result.put("success", true);
            result.put("message", "Successfully uploaded " + uploaded + " new vectors to Pinecone");
            result.put("totalUploaded", uploaded);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Upload ALL embeddings to Pinecone vector database (full refresh)
     */
    @PostMapping("/upload-all-to-pinecone")
    @Transactional
    public Map<String, Object> uploadAllToPinecone() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get all chunks with embeddings
            List<Chunk> chunks = chunkRepository.findAll();
            List<Chunk> chunksWithEmbeddings = new ArrayList<>();
            
            for (Chunk chunk : chunks) {
                if (chunk.getEmbedding() != null && chunk.getEmbedding().length > 0) {
                    chunksWithEmbeddings.add(chunk);
                }
            }
            
            result.put("totalChunks", chunks.size());
            result.put("chunksWithEmbeddings", chunksWithEmbeddings.size());
            
            if (chunksWithEmbeddings.isEmpty()) {
                result.put("message", "No chunks with embeddings found. Generate embeddings first.");
                result.put("success", false);
                return result;
            }
            
            // Skip getting index stats before upload to avoid serialization issues
            // The stats contain protobuf objects that can't be serialized to JSON
            result.put("indexStatsBefore", null);
            
            // Process in batches of 100 for Pinecone
            int batchSize = 100;
            int uploaded = 0;
            
            List<PineconeService.VectorData> vectors = new ArrayList<>();
            
            for (Chunk chunk : chunksWithEmbeddings) {
                // Convert float[] to List<Float>
                List<Float> values = new ArrayList<>();
                for (float val : chunk.getEmbedding()) {
                    values.add(val);
                }
                
                // Create metadata (handle null values)
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("chunkId", chunk.getId());
                metadata.put("bookId", chunk.getBook().getId());
                metadata.put("bookTitle", chunk.getBook().getTitle());
                if (chunk.getChapter() != null) {
                    metadata.put("chapter", chunk.getChapter());
                }
                if (chunk.getPageNum() != null) {
                    metadata.put("pageNum", chunk.getPageNum());
                }
                metadata.put("content", chunk.getContent());
                
                // Create vector data
                PineconeService.VectorData vector = new PineconeService.VectorData(
                    "chunk-" + chunk.getId(),
                    values,
                    metadata
                );
                vectors.add(vector);
                
                // Upload batch when we reach the batch size
                if (vectors.size() >= batchSize) {
                    pineconeService.upsertVectors(vectors);
                    uploaded += vectors.size();
                    vectors.clear();
                    result.put("uploadedSoFar", uploaded);
                }
            }
            
            // Upload remaining vectors
            if (!vectors.isEmpty()) {
                pineconeService.upsertVectors(vectors);
                uploaded += vectors.size();
            }
            
            // Mark ALL chunks as uploaded (since this is a full refresh)
            for (Chunk chunk : chunksWithEmbeddings) {
                chunk.setUploadedToPinecone(true);
            }
            chunkRepository.saveAll(chunksWithEmbeddings);
            
            // Skip getting index stats after upload to avoid serialization issues
            // The stats contain protobuf objects that can't be serialized to JSON
            result.put("indexStatsAfter", null);
            
            result.put("success", true);
            result.put("message", "Successfully uploaded " + uploaded + " vectors to Pinecone (full refresh)");
            result.put("totalUploaded", uploaded);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Delete all vectors from Pinecone index
     * This is useful when you need to clean up orphaned vectors after reprocessing
     */
    @PostMapping("/delete-all-pinecone-vectors")
    @Transactional
    public Map<String, Object> deleteAllPineconeVectors() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("Starting deletion of all vectors from Pinecone index");
            
            // Get current stats before deletion
            Map<String, Object> statsBefore = pineconeService.getIndexStats();
            result.put("vectorsBeforeDeletion", statsBefore.get("totalVectorCount"));
            
            // Delete all vectors using the deleteAll method
            logger.info("Deleting all vectors from Pinecone index");
            pineconeService.deleteAllVectors();
            
            // Mark all chunks as not uploaded to Pinecone
            List<Chunk> allChunks = chunkRepository.findAll();
            for (Chunk chunk : allChunks) {
                chunk.setUploadedToPinecone(false);
            }
            chunkRepository.saveAll(allChunks);
            logger.info("Reset uploadedToPinecone flag for {} chunks", allChunks.size());
            
            // Get stats after deletion (with a small delay for Pinecone to update)
            Thread.sleep(1000); // Wait 1 second for Pinecone to process deletions
            Map<String, Object> statsAfter = pineconeService.getIndexStats();
            result.put("vectorsAfterDeletion", statsAfter.get("totalVectorCount"));
            
            result.put("success", true);
            result.put("message", String.format(
                "Deletion complete. Vectors before: %s, Vectors after: %s, Reset %d chunks in database",
                statsBefore.get("totalVectorCount"),
                statsAfter.get("totalVectorCount"),
                allChunks.size()
            ));
            result.put("chunksReset", allChunks.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.error("Error deleting vectors from Pinecone", e);
        }
        
        return result;
    }
    
    /**
     * Check Pinecone connection and index status
     */
    @GetMapping("/check-pinecone")
    public Map<String, Object> checkPineconeConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> stats = pineconeService.getIndexStats();
            response.put("connected", true);
            response.put("indexName", pineconeIndexName);
            response.put("dimension", stats.get("dimension"));
            response.put("totalVectorCount", stats.get("totalVectorCount"));
            response.put("indexFullness", stats.get("indexFullness"));
        } catch (Exception e) {
            response.put("connected", false);
            response.put("error", e.getMessage());
            
            // Provide helpful error message
            if (e.getMessage().contains("not found")) {
                response.put("error", "Index '" + pineconeIndexName + "' not found. Please create it in Pinecone Console.");
            } else if (e.getMessage().contains("Unauthorized")) {
                response.put("error", "Invalid API key. Please check your PINECONE_API_KEY environment variable.");
            }
        }
        
        return response;
    }
    
    /**
     * Test HTML extraction directly without chunking
     * This helps debug where content is being lost in the extraction process
     */
    @PostMapping("/test-html-extraction")
    public Map<String, Object> testHTMLExtraction(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Save uploaded file temporarily
            String originalFilename = file.getOriginalFilename();
            if (!originalFilename.toLowerCase().endsWith(".html") && !originalFilename.toLowerCase().endsWith(".htm")) {
                result.put("success", false);
                result.put("error", "Only HTML files are supported for this test");
                return result;
            }
            
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            Path tempFile = Files.createTempFile("test-html-", extension);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("Testing HTML extraction for file: {}", originalFilename);
            
            // Use reflection to access the private method for testing
            java.lang.reflect.Method readHTMLMethod = DocumentProcessingService.class.getDeclaredMethod("readHTMLWithEncoding", String.class);
            readHTMLMethod.setAccessible(true);
            String htmlContent = (String) readHTMLMethod.invoke(documentProcessingService, tempFile.toString());
            
            java.lang.reflect.Method extractHTMLMethod = DocumentProcessingService.class.getDeclaredMethod("extractTextFromHTMLSimple", String.class);
            extractHTMLMethod.setAccessible(true);
            String extractedText = (String) extractHTMLMethod.invoke(documentProcessingService, htmlContent);
            
            // Return detailed extraction results
            result.put("success", true);
            result.put("originalFilename", originalFilename);
            result.put("rawHTMLLength", htmlContent.length());
            result.put("extractedTextLength", extractedText.length());
            result.put("rawHTMLSample", htmlContent.length() > 1000 ? htmlContent.substring(0, 1000) : htmlContent);
            result.put("extractedTextSample", extractedText.length() > 1000 ? extractedText.substring(0, 1000) : extractedText);
            result.put("extractedTextFull", extractedText); // Full text for analysis
            
            // Basic analysis
            result.put("containsMainTextDiv", htmlContent.contains("<div class=\"main_text\">"));
            result.put("containsJapaneseText", extractedText.matches(".*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF].*"));
            result.put("estimatedChunksIfProcessed", (extractedText.length() / 1500) + 1);
            
            // Clean up
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.error("Error testing HTML extraction", e);
        }
        
        return result;
    }
    
    /**
     * Run database migration to add uploaded_to_pinecone column
     * NOTE: This migration has been completed as of 2025-05-28
     * Keeping this endpoint for reference/future migrations
     */
    @PostMapping("/run-db-migration")
    @Transactional
    public Map<String, Object> runDatabaseMigration() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Execute the SQL to add the uploaded_to_pinecone column
            String sql = "ALTER TABLE chunks ADD COLUMN IF NOT EXISTS uploaded_to_pinecone BOOLEAN NOT NULL DEFAULT false";
            
            // We need to use the EntityManager to execute native SQL
            // For now, let's return a success message and advise manual execution
            
            response.put("success", true);
            response.put("message", "Please run the following SQL manually in your database:\n\n" +
                        "ALTER TABLE chunks ADD COLUMN uploaded_to_pinecone BOOLEAN NOT NULL DEFAULT false;\n" +
                        "CREATE INDEX idx_chunks_uploaded_to_pinecone ON chunks(uploaded_to_pinecone);");
            
        } catch (Exception e) {
            logger.error("Error running database migration", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }
}