package com.bungakukensaku.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Enhanced EPUB chapter extractor that preserves ruby tags for furigana display
 * 
 * This extractor:
 * - Preserves HTML ruby tags during text extraction
 * - Converts ruby tags to a storage-friendly format
 * - Maintains proper chapter detection and structure
 */
@Service
public class EPUBRubyPreservingExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(EPUBRubyPreservingExtractor.class);
    
    /**
     * Extract chapters with preserved ruby tags
     */
    public List<EPUBChapterExtractor.ChapterContent> extractChaptersWithRuby(String epubPath) {
        List<EPUBChapterExtractor.ChapterContent> chapters = new ArrayList<>();
        
        try (ZipFile zipFile = new ZipFile(new File(epubPath))) {
            // Use same logic as base extractor but with ruby preservation
            String opfPath = findOPFPath(zipFile);
            List<String> spineFiles = parseSpine(zipFile, opfPath);
            
            // Extract with ruby preservation
            chapters = extractNumberedChaptersWithRuby(zipFile, spineFiles);
            
            if (chapters.isEmpty()) {
                chapters = extractContentBasedChaptersWithRuby(zipFile, spineFiles);
            }
            
        } catch (Exception e) {
            logger.error("Error extracting chapters with ruby: {}", e.getMessage(), e);
        }
        
        return chapters;
    }
    
    // Copy necessary private methods from parent class
    private String findOPFPath(ZipFile zipFile) throws Exception {
        ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
        if (containerEntry == null) {
            throw new Exception("No META-INF/container.xml found in EPUB");
        }
        
        try (InputStream is = zipFile.getInputStream(containerEntry)) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(is);
            
            NodeList rootfiles = doc.getElementsByTagName("rootfile");
            if (rootfiles.getLength() > 0) {
                Element rootfile = (Element) rootfiles.item(0);
                return rootfile.getAttribute("full-path");
            }
        }
        
        throw new Exception("Could not find OPF file path");
    }
    
    private List<String> parseSpine(ZipFile zipFile, String opfPath) throws Exception {
        List<String> spineFiles = new ArrayList<>();
        String basePath = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);
        
        ZipEntry opfEntry = zipFile.getEntry(opfPath);
        try (InputStream is = zipFile.getInputStream(opfEntry)) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(is);
            
            // Get manifest to map IDs to files
            NodeList manifestItems = doc.getElementsByTagName("item");
            java.util.Map<String, String> idToHref = new java.util.HashMap<>();
            
            for (int i = 0; i < manifestItems.getLength(); i++) {
                Element item = (Element) manifestItems.item(i);
                String id = item.getAttribute("id");
                String href = item.getAttribute("href");
                idToHref.put(id, href);
            }
            
            // Get spine items in order
            NodeList spineItems = doc.getElementsByTagName("itemref");
            for (int i = 0; i < spineItems.getLength(); i++) {
                Element itemref = (Element) spineItems.item(i);
                String idref = itemref.getAttribute("idref");
                String href = idToHref.get(idref);
                if (href != null) {
                    spineFiles.add(basePath + href);
                }
            }
        }
        
        return spineFiles;
    }
    
    /**
     * Extract text content from a chapter file while preserving ruby tags
     */
    private String extractChapterText(ZipFile zipFile, String fileName) {
        try {
            ZipEntry entry = zipFile.getEntry(fileName);
            if (entry == null) {
                logger.warn("Could not find chapter file: {}", fileName);
                return "";
            }
            
            try (InputStream is = zipFile.getInputStream(entry)) {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document doc = builder.parse(is);
                
                // Get the body element
                NodeList bodies = doc.getElementsByTagName("body");
                if (bodies.getLength() > 0) {
                    Element body = (Element) bodies.item(0);
                    // Extract text while preserving ruby tags
                    return extractTextWithRuby(body);
                }
                
                return "";
                
            } catch (Exception e) {
                logger.error("Error parsing chapter {}: {}", fileName, e.getMessage());
                return "";
            }
            
        } catch (Exception e) {
            logger.error("Error reading chapter file {}: {}", fileName, e.getMessage());
            return "";
        }
    }
    
    /**
     * Extract text content while preserving ruby tags in a special format
     * Ruby tags are converted to: {kanji|furigana} format for storage
     * 
     * @param node DOM node to process
     * @return Text content with preserved ruby markup
     */
    private String extractTextWithRuby(Node node) {
        StringBuilder result = new StringBuilder();
        
        if (node.getNodeType() == Node.TEXT_NODE) {
            // Plain text node
            result.append(node.getTextContent());
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String tagName = element.getTagName().toLowerCase();
            
            if ("ruby".equals(tagName)) {
                // Handle ruby tag specially
                result.append(extractRubyContent(element));
            } else if ("br".equals(tagName)) {
                // Preserve line breaks
                result.append("\n");
            } else if ("p".equals(tagName) || "div".equals(tagName)) {
                // Add newlines for block elements
                NodeList children = node.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    result.append(extractTextWithRuby(children.item(i)));
                }
                result.append("\n\n");
            } else {
                // For other elements, just extract child content
                NodeList children = node.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    result.append(extractTextWithRuby(children.item(i)));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Extract content from a ruby element
     * Format: {kanji|furigana}
     * 
     * @param rubyElement The ruby element
     * @return Formatted ruby content
     */
    private String extractRubyContent(Element rubyElement) {
        StringBuilder kanji = new StringBuilder();
        StringBuilder furigana = new StringBuilder();
        
        NodeList children = rubyElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            
            if (child.getNodeType() == Node.TEXT_NODE) {
                // Base text (kanji)
                kanji.append(child.getTextContent());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName().toLowerCase();
                
                if ("rt".equals(tagName)) {
                    // Ruby text (furigana)
                    furigana.append(childElement.getTextContent());
                } else if ("rb".equals(tagName)) {
                    // Ruby base (some EPUBs use rb tag)
                    kanji.append(childElement.getTextContent());
                } else if ("rp".equals(tagName)) {
                    // Ruby parentheses - ignore
                } else {
                    // Other elements inside ruby, treat as base text
                    kanji.append(extractTextWithRuby(child));
                }
            }
        }
        
        // Return in our storage format
        if (!furigana.toString().isEmpty()) {
            return "{" + kanji.toString().trim() + "|" + furigana.toString().trim() + "}";
        } else {
            // No furigana found, just return the kanji
            return kanji.toString();
        }
    }
    
    /**
     * Extract chapters by looking for numbered chapter markers with ruby preservation
     * EXPERIMENTAL: Chapter pattern detection disabled - use spine structure instead
     */
    private List<EPUBChapterExtractor.ChapterContent> extractNumberedChaptersWithRuby(ZipFile zipFile, List<String> spineFiles) {
        // Chapter pattern detection disabled - fall back to spine-based processing
        // This avoids false matches where "第二章" appears in narrative text
        logger.debug("Chapter pattern detection disabled - falling back to spine-based extraction");
        return new ArrayList<>(); // Force fallback to extractContentBasedChaptersWithRuby()
    }
    
    /**
     * Extract chapters by content with ruby preservation
     */
    private List<EPUBChapterExtractor.ChapterContent> extractContentBasedChaptersWithRuby(ZipFile zipFile, List<String> spineFiles) {
        List<EPUBChapterExtractor.ChapterContent> chapters = new ArrayList<>();
        int chapterNumber = 1;
        int totalPosition = 0;
        
        for (String fileName : spineFiles) {
            String chapterText = extractChapterText(zipFile, fileName);
            
            // Skip if too small
            // EXPERIMENTAL: Size filtering disabled - process all content regardless of size
            // This allows books with shorter sections (like quotations) to be processed
            if (false && chapterText.length() < 5000) {
                logger.debug("Skipping {} - too small ({} chars)", fileName, chapterText.length());
                continue;
            } else if (chapterText.length() < 5000) {
                logger.debug("Processing {} despite small size ({} chars) - size filtering disabled", fileName, chapterText.length());
            }
            
            // Skip non-chapter content
            if (isNonChapterContent(chapterText)) {
                logger.debug("Skipping {} - detected as non-chapter content", fileName);
                continue;
            }
            
            String chapterTitle = extractChapterTitle(chapterText);
            if (chapterTitle == null) {
                chapterTitle = "第" + chapterNumber + "章";
            }
            
            EPUBChapterExtractor.ChapterContent chapter = new EPUBChapterExtractor.ChapterContent(chapterNumber, chapterTitle);
            chapter.startPosition = totalPosition;
            chapter.fullText = chapterText;
            chapter.length = chapterText.length();
            
            totalPosition += chapter.length;
            chapters.add(chapter);
            chapterNumber++;
            
            logger.info("Extracted content chapter with ruby {}: {} ({} characters)", 
                chapter.chapterNumber, chapter.title, chapter.length);
        }
        
        return chapters;
    }
    
    /**
     * Check if content is likely non-chapter (appendix, references, etc.)
     * EXPERIMENTAL: Filtering disabled - process all content and let AI determine relevance
     */
    private boolean isNonChapterContent(String text) {
        // Filtering disabled - process all content
        // Let the AI determine what's relevant during search rather than pre-filtering
        logger.debug("Content filtering disabled - processing all content");
        return false;
    }
    
    /**
     * Extract chapter title from the beginning of chapter content
     */
    private String extractChapterTitle(String text) {
        String[] lines = text.split("\\n");
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.length() < 2) {
                continue;
            }
            
            // Skip metadata lines
            if (line.contains("第") && line.contains("巻")) {
                continue;
            }
            if (line.contains("池田大作") || line.contains("聖教新聞社")) {
                continue;
            }
            
            // Look for chapter title patterns
            if (line.length() >= 2 && line.length() <= 20) {
                if (!line.contains("http") && !line.contains("ISBN") && 
                    !line.contains("発行") && !line.contains("版")) {
                    return line;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Helper class for chapter matching
     */
    private static class ChapterMatch {
        String title;
        int startIndex;
    }
    
    /**
     * Convert our ruby storage format back to HTML for display
     * {kanji|furigana} → <ruby>kanji<rt>furigana</rt></ruby>
     * 
     * @param text Text with ruby markup in storage format
     * @return HTML with proper ruby tags
     */
    public static String convertToHtmlRuby(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Pattern to match our ruby format: {kanji|furigana}
        return text.replaceAll("\\{([^|]+)\\|([^}]+)\\}", "<ruby>$1<rt>$2</rt></ruby>");
    }
}