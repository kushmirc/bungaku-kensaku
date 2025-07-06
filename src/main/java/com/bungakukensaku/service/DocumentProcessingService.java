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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        String fullText = null;
        
        try {
            // Determine file type and extract text accordingly
            if (filePath.toLowerCase().endsWith(".pdf")) {
                logger.info("Processing as PDF file");
                fullText = extractTextFromPDF(filePath);
                logger.info("Extracted {} characters from PDF", fullText.length());
                chunks = createChunks(fullText, book);
            } else if (filePath.toLowerCase().endsWith(".epub")) {
                logger.info("Processing as EPUB file with chapter awareness");
                // Use chapter-aware processing for EPUBs
                chunks = processEPUBWithChapters(filePath, book);
                logger.info("Created {} chunks from EPUB with chapter data", chunks.size());
                // For EPUB, reconstruct full text from chunks for HTML generation
                fullText = reconstructFullTextFromChunks(chunks);
            } else if (filePath.toLowerCase().endsWith(".html") || filePath.toLowerCase().endsWith(".htm")) {
                logger.info("Processing as HTML file: {}", filePath);
                // Check if HTML has chapter structure for intelligent processing
                boolean hasChapters = hasChapterStructure(filePath);
                logger.info("HTML chapter structure check result: {}", hasChapters);
                
                if (hasChapters) {
                    logger.info("HTML file has chapter structure - using chapter-aware processing");
                    chunks = processHTMLWithChapters(filePath, book);
                    logger.info("Created {} chunks from HTML with chapter data", chunks.size());
                    // For structured HTML, reconstruct full text from chunks
                    fullText = reconstructFullTextFromChunks(chunks);
                } else {
                    logger.info("HTML file is simple document - using text extraction");
                    fullText = extractTextFromHTML(filePath);
                    logger.info("Extracted {} characters from HTML", fullText.length());
                    chunks = createChunks(fullText, book);
                }
            } else {
                throw new IllegalArgumentException("Unsupported file format. Only PDF, EPUB, and HTML are supported.");
            }
            
            logger.info("Created {} chunks from document", chunks.size());
            
            // Save full text as static HTML file - but chunks don't have IDs yet
            // We'll need to regenerate after saving
            if (fullText != null && book.getId() != null) {
                String staticPath = saveFullTextAsHTML(fullText, chunks, book);
                book.setStaticTextPath(staticPath);
                logger.info("Saved full text to: {}", staticPath);
            }
            
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
     * Extract text from an HTML file using Apache Tika
     * Handles various encodings including Shift_JIS for Aozora Bunko format
     * 
     * @param htmlPath Path to the HTML file
     * @return Extracted text as a single string
     * @throws IOException if HTML cannot be read
     */
    private String extractTextFromHTML(String htmlPath) throws IOException {
        try {
            // First, read HTML with proper encoding
            String htmlContent = readHTMLWithEncoding(htmlPath);
            
            // Log a sample of what we read to debug encoding issues
            logger.debug("Raw HTML content sample (first 200 chars): {}", 
                htmlContent.length() > 200 ? htmlContent.substring(0, 200) : htmlContent);
            
            // For Aozora Bunko files, let's try a simpler approach without Tika
            // since Tika might be re-encoding incorrectly
            if (htmlContent.contains("青空文庫") || htmlContent.contains("aozora")) {
                logger.debug("Processing as Aozora Bunko file - using simple HTML parsing");
                String text = extractTextFromHTMLSimple(htmlContent);
                // Don't call cleanText() here - extractTextFromHTMLSimple already cleans the text
                logger.info("Extracted {} characters using simple parsing (already cleaned)", text.length());
                return text;
            }
            
            // Use Apache Tika to parse the properly encoded HTML
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // Remove size limit
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            // Convert string to input stream with UTF-8 (since we've already handled encoding)
            try (InputStream stream = new java.io.ByteArrayInputStream(htmlContent.getBytes("UTF-8"))) {
                parser.parse(stream, handler, metadata, context);
            }
            
            // Get extracted text
            String text = handler.toString();
            
            // Clean up the text
            text = cleanText(text);
            
            logger.debug("Extracted {} characters from HTML using Tika", text.length());
            logger.debug("Content type: {}", metadata.get("Content-Type"));
            
            return text;
        } catch (Exception e) {
            logger.error("Error extracting text from HTML file: {}", e.getMessage(), e);
            throw new IOException("Failed to extract text from HTML: " + e.getMessage(), e);
        }
    }
    
    /**
     * Simple HTML text extraction without Tika
     * For cases where Tika causes encoding issues
     * 
     * @param htmlContent Raw HTML content
     * @return Extracted text
     */
    private String extractTextFromHTMLSimple(String htmlContent) {
        logger.info("=== HTML EXTRACTION DEBUG START ===");
        logger.info("Raw HTML content length: {}", htmlContent.length());
        logger.info("HTML content sample (first 500 chars): {}", 
            htmlContent.length() > 500 ? htmlContent.substring(0, 500) : htmlContent);
        
        // Find the main text content - for Aozora Bunko, look for specific patterns
        int startIndex = htmlContent.indexOf("<div class=\"main_text\">");
        if (startIndex == -1) {
            startIndex = htmlContent.indexOf("<div class=main_text>");
        }
        
        logger.info("Found main_text div at index: {}", startIndex);
        
        if (startIndex != -1) {
            // Just extract everything from main_text to the end
            // Don't try to be clever about finding end markers - it causes more problems than it solves
            int endIndex = htmlContent.indexOf("</body>");
            if (endIndex == -1) {
                endIndex = htmlContent.length();
            }
            
            logger.info("End index (</body> or end of file): {}", endIndex);
            logger.info("Section to extract: {} to {} (length: {})", startIndex, endIndex, endIndex - startIndex);
            
            String mainContent = htmlContent.substring(startIndex, endIndex);
            logger.info("STEP 1: Extracted main content section, raw length: {}", mainContent.length());
            logger.info("Main content starts with: {}", 
                mainContent.length() > 200 ? mainContent.substring(0, 200) : mainContent);
            logger.info("Main content ends with: {}", 
                mainContent.length() > 200 ? mainContent.substring(mainContent.length() - 200) : mainContent);
            
            // Remove HTML tags but preserve ruby annotations structure
            String beforeTagReplacement = mainContent;
            mainContent = mainContent.replaceAll("<br\\s*/?>", "\n");
            mainContent = mainContent.replaceAll("<p[^>]*>", "\n");
            mainContent = mainContent.replaceAll("</p>", "\n");
            mainContent = mainContent.replaceAll("<div[^>]*>", "\n");
            mainContent = mainContent.replaceAll("</div>", "\n");
            
            logger.info("STEP 2: After basic tag replacement, length: {} (was: {})", 
                mainContent.length(), beforeTagReplacement.length());
            
            // Handle ruby annotations - keep the base text and pronunciation
            String beforeRubyProcessing = mainContent;
            mainContent = mainContent.replaceAll("<ruby><rb>([^<]+)</rb><rp>[^<]*</rp><rt>([^<]+)</rt><rp>[^<]*</rp></ruby>", "$1($2)");
            
            logger.info("STEP 3: After ruby processing, length: {} (was: {})", 
                mainContent.length(), beforeRubyProcessing.length());
            
            // Remove remaining HTML tags and entities
            String beforeHTMLRemoval = mainContent;
            mainContent = mainContent.replaceAll("<[^>]+>", "");
            mainContent = mainContent.replaceAll("&nbsp;", " ");
            mainContent = mainContent.replaceAll("&[a-zA-Z]+;", "");
            
            logger.info("STEP 4: After all HTML removal, length: {} (was: {})", 
                mainContent.length(), beforeHTMLRemoval.length());
            
            // Clean up whitespace and line breaks more carefully
            String beforeWhitespaceCleanup = mainContent;
            mainContent = mainContent.replaceAll("　+", "　"); // Clean up multiple Japanese spaces
            mainContent = mainContent.replaceAll("[ \\t]+", " "); // Clean up spaces/tabs but preserve newlines
            mainContent = mainContent.replaceAll("\\n\\s*\\n+", "\n\n"); // Normalize multiple newlines
            mainContent = mainContent.replaceAll("^\\s+", ""); // Remove leading whitespace
            
            logger.info("STEP 5: After whitespace cleaning, length: {} (was: {})", 
                mainContent.length(), beforeWhitespaceCleanup.length());
            
            String result = mainContent.trim();
            logger.info("STEP 6: Final extracted text length after trim: {}", result.length());
            logger.info("Final text starts with: {}", 
                result.length() > 200 ? result.substring(0, 200) : result);
            logger.info("Final text ends with: {}", 
                result.length() > 200 ? result.substring(result.length() - 200) : result);
            logger.info("=== HTML EXTRACTION DEBUG END ===");
            
            return result;
        }
        
        logger.warn("Could not find main_text div, trying to extract from body");
        
        // Fallback: extract everything between body tags
        int bodyStart = htmlContent.indexOf("<body>");
        int bodyEnd = htmlContent.indexOf("</body>");
        if (bodyStart != -1 && bodyEnd != -1) {
            String bodyContent = htmlContent.substring(bodyStart, bodyEnd);
            
            // Remove all HTML tags
            bodyContent = bodyContent.replaceAll("<[^>]+>", " ");
            bodyContent = bodyContent.replaceAll("\\s+", " ");
            
            return bodyContent.trim();
        }
        
        return "";
    }
    
    /**
     * Check if HTML file has structured chapter organization
     * Looks for Aozora Bunko heading patterns
     * 
     * @param htmlPath Path to the HTML file
     * @return true if the HTML has chapter structure, false otherwise
     */
    private boolean hasChapterStructure(String htmlPath) {
        try (InputStream stream = new FileInputStream(new File(htmlPath))) {
            // Read the HTML content to look for chapter markers
            String content = new String(stream.readAllBytes(), "Shift_JIS");
            
            // Look for Aozora Bunko chapter patterns
            boolean hasMainChapters = content.contains("o-midashi") || content.contains("<h3");
            boolean hasSubChapters = content.contains("naka-midashi") || content.contains("<h4");
            boolean hasChapterAnchors = content.contains("midashi_anchor");
            
            logger.info("Chapter structure analysis for {}: mainChapters={}, subChapters={}, anchors={}", 
                htmlPath, hasMainChapters, hasSubChapters, hasChapterAnchors);
            
            // Consider it structured if it has main chapters or multiple subsections
            boolean result = hasMainChapters || (hasSubChapters && hasChapterAnchors);
            logger.info("File {} has chapter structure: {}", htmlPath, result);
            return result;
            
        } catch (Exception e) {
            logger.warn("Could not analyze HTML structure for {}: {}", htmlPath, e.getMessage());
            // Default to simple processing if we can't analyze
            return false;
        }
    }
    
    /**
     * Clean and normalize extracted text
     * 
     * @param text Raw text from PDF
     * @return Cleaned text
     */
    private String cleanText(String text) {
        logger.info("cleanText - input length: {}", text.length());
        
        // Remove multiple consecutive newlines
        text = text.replaceAll("\\n{3,}", "\n\n");
        
        // Remove page numbers that appear alone on lines (common in books)
        text = text.replaceAll("(?m)^\\s*\\d+\\s*$", "");
        
        // Normalize spaces (remove multiple spaces but preserve newlines)
        text = text.replaceAll("[ \\t]{2,}", " ");
        
        // Trim each line
        String[] lines = text.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleaned.append(trimmed).append("\n");
            }
        }
        
        String result = cleaned.toString().trim();
        logger.info("cleanText - output length: {}", result.length());
        return result;
    }
    
    /**
     * Split text into overlapping chunks
     * 
     * @param text Full text to chunk
     * @param book Book entity for metadata
     * @return List of chunk entities
     */
    private List<Chunk> createChunks(String text, Book book) {
        logger.info("=== CHUNKING DEBUG START ===");
        logger.info("Input text length for chunking: {}", text.length());
        logger.info("Text starts with: {}", 
            text.length() > 200 ? text.substring(0, 200) : text);
        logger.info("Text ends with: {}", 
            text.length() > 200 ? text.substring(text.length() - 200) : text);
        
        List<Chunk> chunks = new ArrayList<>();
        
        // Calculate chunk size in characters
        int chunkSizeChars = CHUNK_SIZE * CHARS_PER_TOKEN;
        int overlapChars = OVERLAP_SIZE * CHARS_PER_TOKEN;
        
        logger.info("Chunk size: {} chars, Overlap: {} chars", chunkSizeChars, overlapChars);
        
        int textLength = text.length();
        int chunkNumber = 0;
        int totalCharactersProcessed = 0;
        
        int currentStart = 0;
        while (currentStart < textLength) {
            int end = Math.min(currentStart + chunkSizeChars, textLength);
            
            String chunkText = text.substring(currentStart, end);
            
            // Try to break at a sentence boundary
            if (end < textLength) {
                int lastPeriod = chunkText.lastIndexOf("。");
                if (lastPeriod > chunkSizeChars * 0.8) {
                    end = currentStart + lastPeriod + 1;
                    chunkText = text.substring(currentStart, end);
                }
            }
            
            // Create chunk entity
            Chunk chunk = new Chunk();
            chunk.setBook(book);
            chunk.setContent(chunkText);
            chunk.setChapter(estimateChapter(chunkText, chunkNumber));
            chunk.setPageNum(estimatePage(currentStart, textLength));
            chunk.setMetadata(createMetadata(chunkNumber, currentStart, end));
            
            totalCharactersProcessed += chunkText.length();
            
            logger.info("Chunk {}: start={}, end={}, length={}, chars processed so far: {}", 
                chunkNumber, currentStart, end, chunkText.length(), totalCharactersProcessed);
            logger.info("Chunk {} content sample: {}", chunkNumber, 
                chunkText.length() > 100 ? chunkText.substring(0, 100) : chunkText);
            
            chunks.add(chunk);
            chunkNumber++;
            
            // Calculate next start position
            if (end < textLength && text.charAt(end - 1) == '。') {
                // If we broke at sentence boundary, start next chunk with overlap from that point
                currentStart = end - overlapChars;
            } else {
                // Normal progression with overlap
                currentStart = currentStart + (chunkSizeChars - overlapChars);
            }
        }
        
        logger.info("=== CHUNKING SUMMARY ===");
        logger.info("Total chunks created: {}", chunks.size());
        logger.info("Total characters in input: {}", textLength);
        logger.info("Total characters processed across all chunks: {}", totalCharactersProcessed);
        logger.info("=== CHUNKING DEBUG END ===");
        
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
            
            // Always preserve content - don't discard remaining text
            // Small chunks at the end are better than lost content
            
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
    
    /**
     * Process HTML file with chapter structure awareness
     * Extracts chapters based on Aozora Bunko heading patterns
     * 
     * @param htmlPath Path to the HTML file
     * @param book Book entity
     * @return List of chunks organized by chapters
     */
    private List<Chunk> processHTMLWithChapters(String htmlPath, Book book) {
        logger.info("=== HTML CHAPTER PROCESSING START for {} ===", htmlPath);
        List<Chunk> allChunks = new ArrayList<>();
        
        try {
            // Read the HTML content with proper encoding
            String content = readHTMLWithEncoding(htmlPath);
            logger.info("Read HTML content for chapter processing, length: {}", content.length());
            
            // Extract chapter structure
            List<HTMLChapter> chapters = extractChaptersFromHTML(content);
            logger.info("Extracted {} chapters from HTML", chapters.size());
            
            // Process each chapter
            for (int i = 0; i < chapters.size(); i++) {
                HTMLChapter chapter = chapters.get(i);
                logger.info("Processing chapter {}: '{}' with {} chars of content", 
                    i + 1, chapter.title, chapter.cleanedContent != null ? chapter.cleanedContent.length() : 0);
                List<Chunk> chapterChunks = createChunksFromHTMLChapter(chapter, book, i + 1);
                allChunks.addAll(chapterChunks);
                logger.info("Created {} chunks from chapter: {}", 
                    chapterChunks.size(), chapter.title);
            }
            
            // Log total characters processed
            long totalChars = allChunks.stream()
                .mapToLong(chunk -> chunk.getContent().length())
                .sum();
            logger.info("=== HTML CHAPTER PROCESSING END: Created {} chunks with {} total chars ===", 
                allChunks.size(), totalChars);
            
        } catch (Exception e) {
            logger.error("Error processing HTML with chapters: {}", e.getMessage(), e);
            // Fall back to non-chapter processing
            logger.info("Falling back to standard HTML processing");
            try {
                String fullText = extractTextFromHTML(htmlPath);
                return createChunks(fullText, book);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to process HTML", ioe);
            }
        }
        
        return allChunks;
    }
    
    /**
     * Read HTML content with proper encoding detection
     * 
     * @param htmlPath Path to HTML file
     * @return Raw HTML content as string
     * @throws IOException if file cannot be read
     */
    private String readHTMLWithEncoding(String htmlPath) throws IOException {
        File htmlFile = new File(htmlPath);
        
        try (InputStream stream = new FileInputStream(htmlFile)) {
            byte[] bytes = stream.readAllBytes();
            
            // First, try to read as UTF-8 to check for charset declaration
            String utf8Content = new String(bytes, "UTF-8");
            
            // Look for charset declaration in HTML meta tag
            if (utf8Content.contains("charset=Shift_JIS") || utf8Content.contains("charset=\"Shift_JIS\"")) {
                logger.debug("Detected Shift_JIS encoding from HTML meta tag");
                String shiftJisContent = new String(bytes, "Shift_JIS");
                logger.debug("Successfully read file with Shift_JIS encoding, content length: {}", shiftJisContent.length());
                return shiftJisContent;
            } else if (utf8Content.contains("charset=UTF-8") || utf8Content.contains("charset=\"UTF-8\"")) {
                logger.debug("Detected UTF-8 encoding from HTML meta tag");
                return utf8Content;
            } else {
                // For Aozora Bunko files, try Shift_JIS by default
                try {
                    String shiftJisContent = new String(bytes, "Shift_JIS");
                    // Check if we get readable Japanese characters
                    if (shiftJisContent.contains("」") || shiftJisContent.contains("「") || 
                        shiftJisContent.contains("死") || shiftJisContent.contains("問題")) {
                        logger.debug("Using Shift_JIS encoding (detected readable Japanese characters)");
                        return shiftJisContent;
                    }
                } catch (Exception e) {
                    logger.debug("Shift_JIS decoding failed: {}", e.getMessage());
                }
                
                // Fall back to UTF-8
                logger.debug("Using UTF-8 encoding (fallback)");
                return utf8Content;
            }
        }
    }
    
    /**
     * Extract chapter structure from HTML content
     * 
     * @param htmlContent Raw HTML content
     * @return List of chapters with titles and content
     */
    private List<HTMLChapter> extractChaptersFromHTML(String htmlContent) {
        List<HTMLChapter> chapters = new ArrayList<>();
        
        // Simple regex-based extraction for Aozora Bunko format
        // Look for main chapters (o-midashi)
        String chapterPattern = "<h3[^>]*class=\"o-midashi\"[^>]*>.*?<a[^>]*id=\"([^\"]+)\"[^>]*>([^<]+)</a>.*?</h3>";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(chapterPattern, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(htmlContent);
        
        int lastEnd = 0;
        int chapterNum = 1;
        
        while (matcher.find()) {
            String anchorId = matcher.group(1);
            String title = matcher.group(2).trim();
            int chapterStart = matcher.start();
            
            // If this isn't the first chapter, finalize the previous one
            if (!chapters.isEmpty()) {
                HTMLChapter prevChapter = chapters.get(chapters.size() - 1);
                prevChapter.content = htmlContent.substring(prevChapter.startPos, chapterStart);
                prevChapter.cleanedContent = extractTextFromHTMLSnippet(prevChapter.content);
            }
            
            // Create new chapter
            HTMLChapter chapter = new HTMLChapter();
            chapter.title = title;
            chapter.anchorId = anchorId;
            chapter.startPos = chapterStart;
            chapter.chapterNumber = chapterNum++;
            
            chapters.add(chapter);
        }
        
        // Handle the last chapter
        if (!chapters.isEmpty()) {
            HTMLChapter lastChapter = chapters.get(chapters.size() - 1);
            lastChapter.content = htmlContent.substring(lastChapter.startPos);
            lastChapter.cleanedContent = extractTextFromHTMLSnippet(lastChapter.content);
        }
        
        // If no main chapters found, create a single chapter from the main content
        if (chapters.isEmpty()) {
            HTMLChapter singleChapter = new HTMLChapter();
            singleChapter.title = "全文";
            singleChapter.chapterNumber = 1;
            singleChapter.content = htmlContent;
            singleChapter.cleanedContent = extractTextFromHTMLSnippet(htmlContent);
            chapters.add(singleChapter);
        }
        
        return chapters;
    }
    
    /**
     * Extract clean text from HTML snippet using Tika
     * 
     * @param htmlSnippet Raw HTML content
     * @return Clean text content
     */
    private String extractTextFromHTMLSnippet(String htmlSnippet) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1);
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            try (InputStream stream = new java.io.ByteArrayInputStream(htmlSnippet.getBytes("Shift_JIS"))) {
                parser.parse(stream, handler, metadata, context);
                return cleanText(handler.toString());
            }
        } catch (Exception e) {
            logger.warn("Failed to extract text from HTML snippet: {}", e.getMessage());
            // Fall back to simple tag removal
            return htmlSnippet.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
    }
    
    /**
     * Create chunks from HTML chapter
     * 
     * @param chapter HTML chapter with content
     * @param book Book entity
     * @param chapterNumber Chapter number
     * @return List of chunks from this chapter
     */
    private List<Chunk> createChunksFromHTMLChapter(HTMLChapter chapter, Book book, int chapterNumber) {
        List<Chunk> chunks = new ArrayList<>();
        
        String text = chapter.cleanedContent;
        int chunkSizeChars = CHUNK_SIZE * CHARS_PER_TOKEN;
        int overlapChars = OVERLAP_SIZE * CHARS_PER_TOKEN;
        
        int chapterLength = text.length();
        int chunkNumberInChapter = 0;
        
        for (int start = 0; start < chapterLength; start += (chunkSizeChars - overlapChars)) {
            int end = Math.min(start + chunkSizeChars, chapterLength);
            
            // Try to break at sentence boundaries for Japanese text
            if (end < chapterLength) {
                int lastSentence = text.lastIndexOf('。', end);
                if (lastSentence > start + (chunkSizeChars / 2)) {
                    end = lastSentence + 1;
                }
            }
            
            Chunk chunk = new Chunk();
            chunk.setContent(text.substring(start, end));
            chunk.setBook(book);
            chunk.setUploadedToPinecone(false);
            
            // Set chapter information
            chunk.setChapter(chapter.title);
            chunk.setChapterNumber(chapterNumber);
            
            // Calculate position information
            int percentage = (int) ((double) start / chapterLength * 100);
            chunk.setChapterPercentage(percentage);
            chunk.setChapterPosition(start);
            
            // Enhanced metadata for HTML chapters
            chunk.setMetadata(String.format(
                "{\"chunk_number\": %d, \"chapter\": %d, \"chapter_title\": \"%s\", \"anchor_id\": \"%s\", \"position_in_chapter\": %d, \"percentage\": %d}",
                chunkNumberInChapter, chapterNumber, 
                chapter.title.replace("\"", "\\\""), 
                chapter.anchorId != null ? chapter.anchorId : "",
                start, percentage
            ));
            
            chunks.add(chunk);
            chunkNumberInChapter++;
        }
        
        return chunks;
    }
    
    /**
     * Inner class to represent HTML chapter structure
     */
    private static class HTMLChapter {
        String title;
        String anchorId;
        String content;
        String cleanedContent;
        int startPos;
        int chapterNumber;
    }
    
    /**
     * Reconstruct full text from chunks (preserving order)
     * Used when chunks were created with chapter awareness
     * 
     * @param chunks List of chunks in order
     * @return Full text reconstructed from chunks
     */
    private String reconstructFullTextFromChunks(List<Chunk> chunks) {
        StringBuilder fullText = new StringBuilder();
        String lastChapter = "";
        
        for (Chunk chunk : chunks) {
            // Add chapter header if we're entering a new chapter
            if (chunk.getChapter() != null && !chunk.getChapter().equals(lastChapter)) {
                if (fullText.length() > 0) {
                    fullText.append("\n\n");
                }
                fullText.append("【").append(chunk.getChapter()).append("】\n\n");
                lastChapter = chunk.getChapter();
            }
            
            // Add chunk content
            fullText.append(chunk.getContent());
            
            // Add some spacing between chunks
            if (!chunk.getContent().endsWith("\n")) {
                fullText.append("\n");
            }
        }
        
        return fullText.toString();
    }
    
    /**
     * Save full text as HTML file with chunk anchors
     * 
     * @param fullText The complete text of the book
     * @param chunks List of chunks with position info
     * @param book Book entity with metadata
     * @return The static path to the saved HTML file
     * @throws IOException if file cannot be saved
     */
    private String saveFullTextAsHTML(String fullText, List<Chunk> chunks, Book book) throws IOException {
        // Create author directory path using actual Japanese name
        String authorDir = book.getAuthor();
        
        // Create the directory structure
        Path booksDir = Paths.get("src/main/resources/static/books");
        Path authorPath = booksDir.resolve(authorDir);
        Files.createDirectories(authorPath);
        
        // Create filename using actual Japanese book title
        // Add book ID to ensure uniqueness
        String filename = book.getTitle() + "-" + book.getId() + ".html";
        Path filePath = authorPath.resolve(filename);
        
        // Generate HTML content with proper structure and chunk anchors
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"ja\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(escapeHtml(book.getTitle())).append("</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: 'Noto Sans JP', sans-serif; line-height: 1.8; max-width: 800px; margin: 0 auto; padding: 20px; }\n");
        html.append("        .chunk { margin: 1em 0; }\n");
        html.append("        .chunk-highlight { background-color: #ffffcc; padding: 0.5em; border-radius: 4px; }\n");
        html.append("        .chapter-title { font-size: 1.5em; font-weight: bold; margin: 2em 0 1em 0; color: #2c5aa0; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <h1>").append(escapeHtml(book.getTitle())).append("</h1>\n");
        html.append("    <p><strong>著者:</strong> ").append(escapeHtml(book.getAuthor())).append("</p>\n");
        html.append("    <hr>\n");
        html.append("    <div class=\"content\">\n");
        
        // Add content with chunk anchors
        if (chunks != null && !chunks.isEmpty()) {
            String currentChapter = "";
            int currentPosition = 0;
            
            for (Chunk chunk : chunks) {
                // Add chapter header if changed
                if (chunk.getChapter() != null && !chunk.getChapter().equals(currentChapter)) {
                    html.append("        <div class=\"chapter-title\">").append(escapeHtml(chunk.getChapter())).append("</div>\n");
                    currentChapter = chunk.getChapter();
                }
                
                // Add chunk anchor and content
                html.append("        <div class=\"chunk\" id=\"chunk-").append(chunk.getId()).append("\">\n");
                
                // Convert text to HTML paragraphs
                String[] paragraphs = chunk.getContent().split("\n\n");
                for (String paragraph : paragraphs) {
                    if (!paragraph.trim().isEmpty()) {
                        html.append("            <p>").append(escapeHtml(paragraph.trim())).append("</p>\n");
                    }
                }
                
                html.append("        </div>\n");
            }
        } else {
            // No chunks, just add the full text
            String[] paragraphs = fullText.split("\n\n");
            for (String paragraph : paragraphs) {
                if (!paragraph.trim().isEmpty()) {
                    html.append("        <p>").append(escapeHtml(paragraph.trim())).append("</p>\n");
                }
            }
        }
        
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        // Highlight and scroll to chunk if specified in URL hash\n");
        html.append("        window.addEventListener('load', function() {\n");
        html.append("            if (window.location.hash) {\n");
        html.append("                const targetId = window.location.hash.substring(1);\n");
        html.append("                const targetElement = document.getElementById(targetId);\n");
        html.append("                if (targetElement) {\n");
        html.append("                    targetElement.classList.add('chunk-highlight');\n");
        html.append("                    targetElement.scrollIntoView({ behavior: 'smooth', block: 'center' });\n");
        html.append("                }\n");
        html.append("            }\n");
        html.append("        });\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        // Write the HTML file
        Files.write(filePath, html.toString().getBytes(StandardCharsets.UTF_8));
        
        // Return the web-accessible path
        return "/books/" + authorDir + "/" + filename;
    }
    
    /**
     * Escape HTML special characters
     * 
     * @param text Text to escape
     * @return HTML-safe text
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * Create ASCII-safe filename from Japanese text
     * Uses a simple romanization approach for common patterns
     * 
     * @param text Japanese text to convert
     * @return ASCII-safe string
     */
    private String createAsciiSafeName(String text) {
        if (text == null) return "unknown";
        
        // Common author name mappings
        if (text.equals("新渡戸稲造")) return "nitobe-inazo";
        if (text.equals("池田大作")) return "ikeda-daisaku";
        
        // For book titles, use a combination of ID and simplified name
        // Remove all non-ASCII characters and clean up
        String ascii = text.replaceAll("[^\\x00-\\x7F]", "")
                          .replaceAll("[^a-zA-Z0-9]+", "-")
                          .replaceAll("-+", "-")
                          .replaceAll("^-|-$", "")
                          .toLowerCase();
        
        // If nothing left after removing non-ASCII, use a default
        if (ascii.isEmpty()) {
            // Try to extract any numbers
            String numbers = text.replaceAll("[^0-9]+", "");
            if (!numbers.isEmpty()) {
                return "book-" + numbers;
            }
            return "book";
        }
        
        return ascii;
    }
    
    /**
     * Regenerate HTML file with proper chunk IDs after chunks have been saved
     * 
     * @param book Book entity with saved chunks
     * @throws IOException if file cannot be saved
     */
    public void regenerateFullTextHTML(Book book) throws IOException {
        if (book.getStaticTextPath() == null || book.getChunks() == null || book.getChunks().isEmpty()) {
            logger.warn("Cannot regenerate HTML - no static path or chunks for book {}", book.getId());
            return;
        }
        
        // Reconstruct full text from chunks
        String fullText = reconstructFullTextFromChunks(book.getChunks());
        
        // Regenerate HTML with proper chunk IDs
        saveFullTextAsHTML(fullText, book.getChunks(), book);
        logger.info("Regenerated HTML with chunk IDs for book {}", book.getId());
    }
    
}