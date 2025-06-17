package com.bungakukensaku.controller;

import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Chunk;
import com.bungakukensaku.repository.BookRepository;
import com.bungakukensaku.repository.ChunkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller to view chunk contents for verification
 */
@RestController
@RequestMapping("/api/chunks")
public class ChunkViewerController {
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    /**
     * Get first and last chunks for a book to verify complete extraction
     */
    @GetMapping("/book/{bookId}/boundaries")
    public Map<String, Object> getBookBoundaries(@PathVariable Long bookId) {
        Map<String, Object> result = new HashMap<>();
        
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new RuntimeException("Book not found"));
        
        List<Chunk> chunks = chunkRepository.findByBookId(bookId);
        
        result.put("bookTitle", book.getTitle());
        result.put("totalChunks", chunks.size());
        
        if (!chunks.isEmpty()) {
            // Get first chunk
            Chunk firstChunk = chunks.get(0);
            Map<String, Object> first = new HashMap<>();
            first.put("chunkId", firstChunk.getId());
            first.put("content", firstChunk.getContent());
            first.put("length", firstChunk.getContent().length());
            result.put("firstChunk", first);
            
            // Get last chunk
            Chunk lastChunk = chunks.get(chunks.size() - 1);
            Map<String, Object> last = new HashMap<>();
            last.put("chunkId", lastChunk.getId());
            last.put("content", lastChunk.getContent());
            last.put("length", lastChunk.getContent().length());
            result.put("lastChunk", last);
            
            // Calculate total characters
            long totalChars = chunks.stream()
                .mapToLong(chunk -> chunk.getContent().length())
                .sum();
            result.put("totalCharacters", totalChars);
        }
        
        return result;
    }
    
    /**
     * Get a specific chunk by ID
     */
    @GetMapping("/{chunkId}")
    public Map<String, Object> getChunk(@PathVariable Long chunkId) {
        Map<String, Object> result = new HashMap<>();
        
        Chunk chunk = chunkRepository.findById(chunkId)
            .orElseThrow(() -> new RuntimeException("Chunk not found"));
        
        result.put("chunkId", chunk.getId());
        result.put("bookTitle", chunk.getBook().getTitle());
        result.put("content", chunk.getContent());
        result.put("chapter", chunk.getChapter());
        result.put("chapterNumber", chunk.getChapterNumber());
        result.put("chapterPercentage", chunk.getChapterPercentage());
        result.put("pageNum", chunk.getPageNum());
        result.put("metadata", chunk.getMetadata());
        
        return result;
    }
    
    /**
     * Get chapter info summary for all chunks in a book
     */
    @GetMapping("/book/{bookId}/chapter-summary")
    public Map<String, Object> getChapterSummary(@PathVariable Long bookId) {
        Map<String, Object> result = new HashMap<>();
        
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new RuntimeException("Book not found"));
        
        List<Chunk> chunks = chunkRepository.findByBookId(bookId);
        
        result.put("bookTitle", book.getTitle());
        result.put("totalChunks", chunks.size());
        
        int chunksWithChapter = 0;
        int chunksWithoutChapter = 0;
        Map<String, Integer> chapterCounts = new HashMap<>();
        
        for (Chunk chunk : chunks) {
            if (chunk.getChapter() != null && !chunk.getChapter().isEmpty()) {
                chunksWithChapter++;
                chapterCounts.merge(chunk.getChapter(), 1, Integer::sum);
            } else {
                chunksWithoutChapter++;
            }
        }
        
        result.put("chunksWithChapter", chunksWithChapter);
        result.put("chunksWithoutChapter", chunksWithoutChapter);
        result.put("chapterBreakdown", chapterCounts);
        
        // Get some examples of chunks without chapters
        List<Map<String, Object>> noChapterExamples = new ArrayList<>();
        int exampleCount = 0;
        for (Chunk chunk : chunks) {
            if ((chunk.getChapter() == null || chunk.getChapter().isEmpty()) && exampleCount < 5) {
                Map<String, Object> example = new HashMap<>();
                example.put("chunkId", chunk.getId());
                example.put("contentPreview", chunk.getContent().substring(0, Math.min(100, chunk.getContent().length())) + "...");
                noChapterExamples.add(example);
                exampleCount++;
            }
        }
        result.put("examplesWithoutChapter", noChapterExamples);
        
        return result;
    }
}