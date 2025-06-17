package com.bungakukensaku.service;

import com.bungakukensaku.dto.DocumentMetadata;
import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Chunk;
import com.bungakukensaku.service.EPUBChapterExtractor.ChapterContent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.Loader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DocumentProcessingService handles document text extraction and chunking
 * 
 * This service:
 * - Extracts text from PDF and EPUB files
 * - Splits text into manageable chunks for vector embedding
 * - Preserves metadata like page numbers and chapters
 * - Handles Japanese text properly
 * 
 * Design note: This service has a clean interface that can be easily
 * replaced with a Python implementation in the future if needed.
 */
@Service
public class DocumentProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);
    
    @Autowired
    private EPUBChapterExtractor epubChapterExtractor;
    
    @Autowired
    private EPUBRubyPreservingExtractor rubyPreservingExtractor;
    
    // Configuration constants
    private static final int CHUNK_SIZE = 500; // tokens (approximate)
    private static final int OVERLAP_SIZE = 50; // tokens overlap between chunks
    private static final int CHARS_PER_TOKEN = 3; // Rough estimate for Japanese text
    
    /**
     * Process a document (PDF or EPUB) and extract text chunks
     * 
     * @param filePath Path to the document file
     * @param book The book entity this document belongs to
     * @return List of text chunks ready for embedding
     */
    public List<Chunk> processDocument(String filePath, Book book) {
        logger.info("Starting to process document: {}", filePath);
        List<Chunk> chunks = new ArrayList<>();
        
        try {
            String fullText;
            
            // Determine file type and extract text accordingly
            if (filePath.toLowerCase().endsWith(".pdf")) {
                logger.info("Processing as PDF file");
                fullText = extractTextFromPDF(filePath);
                logger.info("Extracted {} characters from PDF", fullText.length());
            } else if (filePath.toLowerCase().endsWith(".epub")) {
                logger.info("Processing as EPUB file with chapter awareness");
                // Use chapter-aware processing for EPUBs
                chunks = processEPUBWithChapters(filePath, book);
                logger.info("Created {} chunks from EPUB with chapter data", chunks.size());
                return chunks; // Early return for EPUB
            } else {
                throw new IllegalArgumentException("Unsupported file format. Only PDF and EPUB are supported.");
            }
            
            // Split into chunks with metadata
            chunks = createChunks(fullText, book);
            logger.info("Created {} chunks from document", chunks.size());
            
        } catch (IOException e) {
            logger.error("Error processing document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process document", e);
        }
        
        return chunks;
    }
    
    /**
     * Extract text from a PDF file
     * 
     * @param pdfPath Path to the PDF file
     * @return Extracted text as a single string
     * @throws IOException if PDF cannot be read
     */
    private String extractTextFromPDF(String pdfPath) throws IOException {
        File pdfFile = new File(pdfPath);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Configure for better Japanese text extraction
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(true);
            
            // Extract all pages
            String text = stripper.getText(document);
            
            // Clean up the text
            text = cleanText(text);
            
            return text;
        }
    }
    
    /**
     * Extract text from specific page range
     * 
     * @param pdfPath Path to the PDF file
     * @param startPage Starting page (1-based)
     * @param endPage Ending page (inclusive)
     * @return Extracted text from specified pages
     * @throws IOException if PDF cannot be read
     */
    public String extractTextFromPages(String pdfPath, int startPage, int endPage) throws IOException {
        File pdfFile = new File(pdfPath);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            stripper.setSortByPosition(true);
            
            return cleanText(stripper.getText(document));
        }
    }
    
    /**
     * Clean and normalize extracted text
     * 
     * @param text Raw text from PDF
     * @return Cleaned text
     */
    private String cleanText(String text) {
        // Remove multiple consecutive newlines
        text = text.replaceAll("\\n{3,}", "\n\n");
        
        // Remove page numbers that appear alone on lines (common in books)
        text = text.replaceAll("(?m)^\\s*\\d+\\s*$", "");
        
        // Normalize spaces (remove multiple spaces)
        text = text.replaceAll("\\s{2,}", " ");
        
        // Trim each line
        String[] lines = text.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleaned.append(trimmed).append("\n");
            }
        }
        
        return cleaned.toString().trim();
    }
    
    /**
     * Split text into overlapping chunks
     * 
     * @param text Full text to chunk
     * @param book Book entity for metadata
     * @return List of chunk entities
     */
    private List<Chunk> createChunks(String text, Book book) {
        List<Chunk> chunks = new ArrayList<>();
        
        // Calculate chunk size in characters
        int chunkSizeChars = CHUNK_SIZE * CHARS_PER_TOKEN;
        int overlapChars = OVERLAP_SIZE * CHARS_PER_TOKEN;
        
        int textLength = text.length();
        int chunkNumber = 0;
        
        for (int start = 0; start < textLength; start += (chunkSizeChars - overlapChars)) {
            int end = Math.min(start + chunkSizeChars, textLength);
            
            // Don't create tiny chunks at the end
            if (end - start < overlapChars && start > 0) {
                break;
            }
            
            String chunkText = text.substring(start, end);
            
            // Try to break at a sentence boundary
            if (end < textLength) {
                int lastPeriod = chunkText.lastIndexOf("。");
                if (lastPeriod > chunkSizeChars * 0.8) {
                    end = start + lastPeriod + 1;
                    chunkText = text.substring(start, end);
                }
            }
            
            // Create chunk entity
            Chunk chunk = new Chunk();
            chunk.setBook(book);
            chunk.setContent(chunkText);
            chunk.setChapter(estimateChapter(chunkText, chunkNumber));
            chunk.setPageNum(estimatePage(start, textLength));
            chunk.setMetadata(createMetadata(chunkNumber, start, end));
            
            chunks.add(chunk);
            chunkNumber++;
            
            // Adjust start position if we broke at sentence boundary
            if (end < textLength && text.charAt(end - 1) == '。') {
                start = end - overlapChars;
            }
        }
        
        return chunks;
    }
    
    /**
     * Estimate chapter based on content
     * This is a simple implementation - can be improved
     */
    private String estimateChapter(String text, int chunkNumber) {
        // Look for chapter markers in Japanese
        if (text.contains("第") && text.contains("章")) {
            // Try to extract chapter number
            // This is simplified - real implementation would be more robust
            return "第" + (chunkNumber / 10 + 1) + "章"; // Rough estimate
        }
        return null;
    }
    
    /**
     * Estimate page number based on position in text
     */
    private Integer estimatePage(int position, int totalLength) {
        // Rough estimate: assume ~2000 chars per page
        return (position / 2000) + 1;
    }
    
    /**
     * Create metadata JSON for chunk
     */
    private String createMetadata(int chunkNumber, int startPos, int endPos) {
        return String.format(
            "{\"chunk_number\": %d, \"start_position\": %d, \"end_position\": %d}",
            chunkNumber, startPos, endPos
        );
    }
    
    /**
     * Extract text from an EPUB file using Apache Tika
     * 
     * @param epubPath Path to the EPUB file
     * @return Extracted text as a single string
     * @throws IOException if EPUB cannot be read
     */
    private String extractTextFromEPUB(String epubPath) throws IOException {
        try {
            // Use BodyContentHandler with -1 to remove character limit
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            Parser parser = new AutoDetectParser();
            
            // Parse the EPUB file
            try (InputStream stream = new FileInputStream(epubPath)) {
                parser.parse(stream, handler, metadata, context);
            }
            
            String text = handler.toString();
            logger.info("Extracted {} characters from EPUB", text.length());
            
            // Clean and return the text
            return cleanText(text);
        } catch (Exception e) {
            logger.error("Error parsing EPUB: {}", e.getMessage());
            throw new IOException("Failed to extract text from EPUB", e);
        }
    }
    
    /**
     * Get document metadata (title, author, etc.) from PDF
     * 
     * @param pdfPath Path to PDF file
     * @return Extracted metadata
     */
    public DocumentMetadata extractMetadata(String pdfPath) {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            DocumentMetadata metadata = new DocumentMetadata();
            
            // Get basic metadata
            metadata.setPageCount(document.getNumberOfPages());
            
            // PDF metadata (if available)
            if (document.getDocumentInformation() != null) {
                metadata.setTitle(document.getDocumentInformation().getTitle());
                metadata.setAuthor(document.getDocumentInformation().getAuthor());
            }
            
            return metadata;
        } catch (IOException e) {
            logger.error("Error extracting metadata: {}", e.getMessage());
            return new DocumentMetadata();
        }
    }
    
    /**
     * Process EPUB file with chapter awareness
     * 
     * @param epubPath Path to the EPUB file
     * @param book Book entity for metadata
     * @return List of chunks with chapter information
     */
    private List<Chunk> processEPUBWithChapters(String epubPath, Book book) {
        List<Chunk> allChunks = new ArrayList<>();
        
        try {
            // Extract chapters using our ruby-preserving extractor
            List<ChapterContent> chapters = rubyPreservingExtractor.extractChaptersWithRuby(epubPath);
            logger.info("Extracted {} chapters from EPUB with ruby preservation", chapters.size());
            
            // Process each chapter
            for (ChapterContent chapter : chapters) {
                List<Chunk> chapterChunks = createChunksFromChapter(chapter, book);
                allChunks.addAll(chapterChunks);
                logger.info("Created {} chunks from chapter {}", 
                    chapterChunks.size(), chapter.chapterNumber);
            }
            
        } catch (Exception e) {
            logger.error("Error processing EPUB with chapters: {}", e.getMessage(), e);
            // Fall back to non-chapter processing
            logger.info("Falling back to standard EPUB processing");
            try {
                String fullText = extractTextFromEPUB(epubPath);
                return createChunks(fullText, book);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to process EPUB", ioe);
            }
        }
        
        return allChunks;
    }
    
    /**
     * Create chunks from a single chapter with position tracking
     * 
     * @param chapter Chapter content with metadata
     * @param book Book entity
     * @return List of chunks from this chapter
     */
    private List<Chunk> createChunksFromChapter(ChapterContent chapter, Book book) {
        List<Chunk> chunks = new ArrayList<>();
        
        String text = chapter.fullText;
        int chunkSizeChars = CHUNK_SIZE * CHARS_PER_TOKEN;
        int overlapChars = OVERLAP_SIZE * CHARS_PER_TOKEN;
        
        int chapterLength = text.length();
        int chunkNumberInChapter = 0;
        
        for (int start = 0; start < chapterLength; start += (chunkSizeChars - overlapChars)) {
            int end = Math.min(start + chunkSizeChars, chapterLength);
            
            // Don't create tiny chunks
            if (end - start < overlapChars && start > 0) {
                break;
            }
            
            String chunkText = text.substring(start, end);
            
            // Try to break at sentence boundary
            if (end < chapterLength) {
                int lastPeriod = chunkText.lastIndexOf("。");
                if (lastPeriod > chunkSizeChars * 0.8) {
                    end = start + lastPeriod + 1;
                    chunkText = text.substring(start, end);
                }
            }
            
            // Create chunk with chapter information
            Chunk chunk = new Chunk();
            chunk.setBook(book);
            chunk.setContent(chunkText);
            chunk.setChapter(chapter.title); // e.g., "第1章"
            chunk.setChapterNumber(chapter.chapterNumber);
            chunk.setChapterPosition(start); // Position within chapter
            chunk.setChapterPercentage(chapter.getPercentagePosition(start));
            
            // Estimate page within chapter (roughly 2000 chars per page)
            int pageInChapter = (start / 2000) + 1;
            chunk.setPageNum(pageInChapter);
            
            // Enhanced metadata
            chunk.setMetadata(String.format(
                "{\"chunk_number\": %d, \"chapter\": %d, \"position_in_chapter\": %d, \"percentage\": %d}",
                chunkNumberInChapter, chapter.chapterNumber, start, chunk.getChapterPercentage()
            ));
            
            chunks.add(chunk);
            chunkNumberInChapter++;
            
            // Adjust start if we broke at sentence
            if (end < chapterLength && text.charAt(end - 1) == '。') {
                start = end - overlapChars;
            }
        }
        
        return chunks;
    }
    
}