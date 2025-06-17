package com.bungakukensaku.controller;

import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Chunk;
import com.bungakukensaku.repository.BookRepository;
import com.bungakukensaku.repository.ChunkRepository;
import com.bungakukensaku.service.DocumentProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test controller for PDF processing functionality
 *
 * This controller provides endpoints to:
 * - Upload and process PDF files
 * - Test text extraction
 * - View chunking results
 * 
 * Note: This is for development/testing only
 */
@RestController
@RequestMapping("/api/test/document")
public class DocumentTestController {
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    /**
     * Test PDF text extraction without saving to database
     * 
     * @param file PDF file to test
     * @return Extracted text preview
     */
    @PostMapping("/extract-text")
    public ResponseEntity<Map<String, Object>> testExtractText(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("test-pdf-", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Extract text from first few pages
            String extractedText = documentProcessingService.extractTextFromPages(
                tempFile.toString(), 1, 3
            );
            
            // Get metadata
            var metadata = documentProcessingService.extractMetadata(tempFile.toString());
            
            response.put("success", true);
            response.put("filename", file.getOriginalFilename());
            response.put("textPreview", extractedText.substring(0, Math.min(1000, extractedText.length())));
            response.put("totalLength", extractedText.length());
            response.put("pageCount", metadata.getPageCount());
            response.put("pdfTitle", metadata.getTitle());
            response.put("pdfAuthor", metadata.getAuthor());
            
            // Clean up temp file
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Process a PDF and save chunks to database
     * 
     * @param file PDF file to process
     * @param bookTitle Title of the book
     * @param author Author name
     * @return Processing results
     */
    @PostMapping("/process-and-save")
    public ResponseEntity<Map<String, Object>> processAndSave(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String bookTitle,
            @RequestParam("author") String author) {
        
        Map<String, Object> response = new HashMap<>();
        
        System.out.println("Processing file: " + file.getOriginalFilename() + " Size: " + file.getSize());
        
        try {
            // Create book entity
            Book book = new Book();
            book.setTitle(bookTitle);
            book.setAuthor(author);
            book.setYear(LocalDateTime.now().getYear()); // Default to current year
            book.setS3FilePath("local://" + file.getOriginalFilename()); // Placeholder
            book = bookRepository.save(book);
            
            // Save uploaded file temporarily with correct extension
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.contains(".")) {
                throw new IllegalArgumentException("Invalid filename: " + originalFilename);
            }
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            Path tempFile = Files.createTempFile("process-doc-", extension);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Process document and create chunks
            List<Chunk> chunks = documentProcessingService.processDocument(
                tempFile.toString(), book
            );
            
            // Save chunks to database
            chunks = chunkRepository.saveAll(chunks);
            
            // Calculate total character count
            long totalCharacters = chunks.stream()
                .mapToLong(chunk -> chunk.getContent().length())
                .sum();
            
            response.put("success", true);
            response.put("bookId", book.getId());
            response.put("bookTitle", book.getTitle());
            response.put("chunksCreated", chunks.size());
            response.put("totalCharacters", totalCharacters);
            response.put("averageChunkSize", chunks.isEmpty() ? 0 : (int)(totalCharacters / chunks.size()));
            response.put("firstChunkPreview", chunks.isEmpty() ? "" : 
                chunks.get(0).getContent().substring(0, Math.min(200, chunks.get(0).getContent().length())));
            response.put("lastChunkPreview", chunks.isEmpty() ? "" : 
                chunks.get(chunks.size()-1).getContent().substring(0, Math.min(200, chunks.get(chunks.size()-1).getContent().length())));
            
            // Clean up temp file
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test chunking algorithm without saving
     * 
     * @param file PDF file to test
     * @return Chunking results
     */
    @PostMapping("/test-chunking")
    public ResponseEntity<Map<String, Object>> testChunking(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("chunk-test-", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Create temporary book entity (not saved)
            Book tempBook = new Book();
            tempBook.setTitle("Test Book");
            tempBook.setAuthor("Test Author");
            
            // Process document
            List<Chunk> chunks = documentProcessingService.processDocument(
                tempFile.toString(), tempBook
            );
            
            // Prepare chunk previews
            List<Map<String, Object>> chunkPreviews = chunks.stream()
                .limit(5) // Show first 5 chunks
                .map(chunk -> {
                    Map<String, Object> preview = new HashMap<>();
                    preview.put("content", chunk.getContent().substring(0, 
                        Math.min(200, chunk.getContent().length())) + "...");
                    preview.put("length", chunk.getContent().length());
                    preview.put("metadata", chunk.getMetadata());
                    return preview;
                })
                .toList();
            
            response.put("success", true);
            response.put("totalChunks", chunks.size());
            response.put("avgChunkLength", chunks.stream()
                .mapToInt(c -> c.getContent().length())
                .average()
                .orElse(0));
            response.put("chunkPreviews", chunkPreviews);
            
            // Clean up temp file
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all books in database
     */
    @GetMapping("/books")
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookRepository.findAll());
    }
    
    /**
     * Get chunks for a specific book
     */
    @GetMapping("/books/{bookId}/chunks")
    public ResponseEntity<Map<String, Object>> getBookChunks(@PathVariable Long bookId) {
        Map<String, Object> response = new HashMap<>();
        
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            response.put("error", "Book not found");
            return ResponseEntity.notFound().build();
        }
        
        List<Chunk> chunks = chunkRepository.findByBookId(bookId);
        response.put("bookTitle", book.getTitle());
        response.put("totalChunks", chunks.size());
        response.put("chunks", chunks.stream()
            .limit(10) // Return first 10 chunks only
            .map(chunk -> {
                Map<String, Object> chunkData = new HashMap<>();
                chunkData.put("id", chunk.getId());
                chunkData.put("contentPreview", chunk.getContent().substring(0, 
                    Math.min(100, chunk.getContent().length())) + "...");
                chunkData.put("pageNumber", chunk.getPageNum());
                return chunkData;
            })
            .toList());
        
        return ResponseEntity.ok(response);
    }
}