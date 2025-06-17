package com.bungakukensaku.controller;

import com.bungakukensaku.service.EPUBStructureParser;
import com.bungakukensaku.service.EPUBStructureParser.ChapterInfo;
import com.bungakukensaku.service.EPUBChapterExtractor;
import com.bungakukensaku.service.EPUBChapterExtractor.ChapterContent;
import com.bungakukensaku.service.DocumentProcessingService;
import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test controller for exploring EPUB structure parsing
 */
@RestController
@RequestMapping("/api/test/epub")
public class EPUBTestController {
    
    @Autowired
    private EPUBStructureParser epubParser;
    
    @Autowired
    private EPUBChapterExtractor epubChapterExtractor;
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    /**
     * Test endpoint to parse EPUB structure
     * Upload an EPUB file and see the extracted structure
     */
    @PostMapping("/parse-structure")
    public ResponseEntity<Map<String, Object>> parseEPUBStructure(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("test-", ".epub");
            file.transferTo(tempFile.toFile());
            
            // Parse the EPUB structure
            List<ChapterInfo> chapters = epubParser.parseEPUBStructure(tempFile.toString());
            
            // Build response
            response.put("success", true);
            response.put("totalChapters", chapters.size());
            
            List<Map<String, Object>> chapterData = new ArrayList<>();
            for (ChapterInfo chapter : chapters) {
                Map<String, Object> chapterMap = new HashMap<>();
                chapterMap.put("number", chapter.chapterNumber);
                chapterMap.put("title", chapter.title);
                chapterMap.put("fileName", chapter.fileName);
                chapterMap.put("textLength", chapter.fullText.length());
                
                // Include text preview
                if (chapter.fullText != null && !chapter.fullText.isEmpty()) {
                    String preview = chapter.fullText.substring(0, 
                        Math.min(200, chapter.fullText.length())) + "...";
                    chapterMap.put("textPreview", preview);
                }
                
                chapterData.add(chapterMap);
            }
            
            response.put("chapters", chapterData);
            
            // Clean up
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test with a local file path (for development)
     */
    @GetMapping("/parse-local")
    public ResponseEntity<Map<String, Object>> parseLocalEPUB(@RequestParam String path) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            File epubFile = new File(path);
            if (!epubFile.exists()) {
                response.put("success", false);
                response.put("error", "File not found: " + path);
                return ResponseEntity.ok(response);
            }
            
            // Parse the EPUB structure
            List<ChapterInfo> chapters = epubParser.parseEPUBStructure(path);
            
            // Build response (same as above)
            response.put("success", true);
            response.put("totalChapters", chapters.size());
            response.put("filePath", path);
            
            List<Map<String, Object>> chapterData = new ArrayList<>();
            for (ChapterInfo chapter : chapters) {
                Map<String, Object> chapterMap = new HashMap<>();
                chapterMap.put("number", chapter.chapterNumber);
                chapterMap.put("title", chapter.title);
                chapterMap.put("fileName", chapter.fileName);
                chapterMap.put("textLength", chapter.fullText.length());
                
                // Include text preview
                if (chapter.fullText != null && !chapter.fullText.isEmpty()) {
                    String preview = chapter.fullText.substring(0, 
                        Math.min(200, chapter.fullText.length())) + "...";
                    chapterMap.put("textPreview", preview);
                }
                
                chapterData.add(chapterMap);
            }
            
            response.put("chapters", chapterData);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test chapter extraction with percentage calculation
     */
    @GetMapping("/test-chapter-percentage")
    public ResponseEntity<Map<String, Object>> testChapterPercentage(@RequestParam String path) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            File epubFile = new File(path);
            if (!epubFile.exists()) {
                response.put("success", false);
                response.put("error", "File not found: " + path);
                return ResponseEntity.ok(response);
            }
            
            // Extract chapters with percentage calculation
            List<ChapterContent> chapters = epubChapterExtractor.extractChapters(path);
            response.put("totalChapters", chapters.size());
            
            // Create a test book entity
            Book testBook = new Book();
            testBook.setTitle("Test Book");
            testBook.setId(999L); // Test ID
            
            // Process with DocumentProcessingService to get chunks
            List<Chunk> chunks = documentProcessingService.processDocument(path, testBook);
            response.put("totalChunks", chunks.size());
            
            // Sample some chunks to show percentage data
            List<Map<String, Object>> sampleChunks = new ArrayList<>();
            int[] sampleIndices = {0, 10, 20, 50, 100, chunks.size() - 1};
            
            for (int idx : sampleIndices) {
                if (idx < chunks.size()) {
                    Chunk chunk = chunks.get(idx);
                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("index", idx);
                    chunkData.put("chapter", chunk.getChapter());
                    chunkData.put("chapterNumber", chunk.getChapterNumber());
                    chunkData.put("chapterPosition", chunk.getChapterPosition());
                    chunkData.put("chapterPercentage", chunk.getChapterPercentage());
                    chunkData.put("contentPreview", chunk.getContent().substring(0, Math.min(100, chunk.getContent().length())) + "...");
                    sampleChunks.add(chunkData);
                }
            }
            
            response.put("sampleChunks", sampleChunks);
            response.put("success", true);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test chapter percentage with file upload
     */
    @PostMapping("/test-chapter-percentage-upload")
    public ResponseEntity<Map<String, Object>> testChapterPercentageUpload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("test-epub-", ".epub");
            file.transferTo(tempFile.toFile());
            
            // Extract chapters with percentage calculation
            List<ChapterContent> chapters = epubChapterExtractor.extractChapters(tempFile.toString());
            response.put("totalChapters", chapters.size());
            
            // Create a test book entity
            Book testBook = new Book();
            testBook.setTitle(file.getOriginalFilename());
            testBook.setId(999L); // Test ID
            
            // Process with DocumentProcessingService to get chunks
            List<Chunk> chunks = documentProcessingService.processDocument(tempFile.toString(), testBook);
            response.put("totalChunks", chunks.size());
            
            // Sample some chunks to show percentage data
            List<Map<String, Object>> sampleChunks = new ArrayList<>();
            int[] sampleIndices = {0, 10, 20, 50, 100, chunks.size() - 1};
            
            for (int idx : sampleIndices) {
                if (idx < chunks.size()) {
                    Chunk chunk = chunks.get(idx);
                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("index", idx);
                    chunkData.put("chapter", chunk.getChapter());
                    chunkData.put("chapterNumber", chunk.getChapterNumber());
                    chunkData.put("chapterPosition", chunk.getChapterPosition());
                    chunkData.put("chapterPercentage", chunk.getChapterPercentage());
                    chunkData.put("contentPreview", chunk.getContent().substring(0, Math.min(100, chunk.getContent().length())) + "...");
                    sampleChunks.add(chunkData);
                }
            }
            
            response.put("sampleChunks", sampleChunks);
            response.put("success", true);
            
            // Clean up
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
}