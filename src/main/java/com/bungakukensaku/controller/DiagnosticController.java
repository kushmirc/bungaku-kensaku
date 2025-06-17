
package com.bungakukensaku.controller;

import com.bungakukensaku.model.Book;
import com.bungakukensaku.repository.BookRepository;
import com.bungakukensaku.repository.ChunkRepository;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostic controller to investigate EPUB extraction issues
 */
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @PostMapping("/check-epub")
    public Map<String, Object> checkEpubExtraction(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Save uploaded file temporarily
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            Path tempFile = Files.createTempFile("diagnostic-", extension);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            File epubFile = tempFile.toFile();
            result.put("fileName", originalFilename);
            result.put("fileSize", file.getSize());
            result.put("fileSizeMB", String.format("%.2f MB", file.getSize() / 1024.0 / 1024.0));
            
            // Method 1: Default Tika parseToString
            Tika tika = new Tika();
            String method1Text = tika.parseToString(epubFile);
            result.put("method1_length", method1Text.length());
            result.put("method1_preview", method1Text.substring(0, Math.min(500, method1Text.length())));
            
            // Method 2: Using BodyContentHandler with no limit
            BodyContentHandler handler = new BodyContentHandler(-1); // -1 means no limit
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            Parser parser = new AutoDetectParser();
            
            try (InputStream stream = Files.newInputStream(tempFile)) {
                parser.parse(stream, handler, metadata, context);
            }
            
            String method2Text = handler.toString();
            result.put("method2_length", method2Text.length());
            result.put("method2_preview", method2Text.substring(0, Math.min(500, method2Text.length())));
            
            // Compare the two methods
            result.put("difference", method2Text.length() - method1Text.length());
            result.put("method1_truncated", method1Text.length() < method2Text.length());
            
            // Estimate pages (rough calculation)
            int charsPerPage = 1000; // Rough estimate for Japanese text
            result.put("estimated_pages_method1", method1Text.length() / charsPerPage);
            result.put("estimated_pages_method2", method2Text.length() / charsPerPage);
            
            // Check for specific truncation point
            if (method1Text.length() < method2Text.length()) {
                result.put("truncation_point", method1Text.length());
                result.put("likely_limit", "Tika default limit is 100,000 characters");
            }
            
            // Clean up
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    @GetMapping("/database-stats")
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        
        for (Book book : bookRepository.findAll()) {
            Map<String, Object> bookStats = new HashMap<>();
            bookStats.put("title", book.getTitle());
            
            Long chunkCount = chunkRepository.countByBookId(book.getId());
            bookStats.put("chunks", chunkCount);
            
            // Get total characters
            Long totalChars = chunkRepository.findByBookId(book.getId()).stream()
                .mapToLong(chunk -> chunk.getContent().length())
                .sum();
            bookStats.put("totalCharacters", totalChars);
            bookStats.put("avgCharsPerChunk", chunkCount > 0 ? totalChars / chunkCount : 0);
            bookStats.put("estimatedPages", totalChars / 1000); // Rough estimate
            
            stats.put("book_" + book.getId(), bookStats);
        }
        
        return stats;
    }
}