package com.bungakukensaku.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Simplified EPUB chapter extractor that focuses on chapter structure
 * and text position rather than paragraph detection
 */
@Service
public class EPUBChapterExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(EPUBChapterExtractor.class);
    
    public static class ChapterContent {
        public int chapterNumber;
        public String title;
        public String fullText;
        public int startPosition; // Character position in the book
        public int length;        // Length of chapter in characters
        
        public ChapterContent(int chapterNumber, String title) {
            this.chapterNumber = chapterNumber;
            this.title = title;
            this.fullText = "";
            this.startPosition = 0;
            this.length = 0;
        }
        
        /**
         * Get the percentage position of a text within this chapter
         */
        public int getPercentagePosition(int characterOffset) {
            if (length == 0) return 0;
            return Math.min(100, (characterOffset * 100) / length);
        }
    }
    
    /**
     * Extract all chapters with their full text content using smart chapter detection
     */
    public List<ChapterContent> extractChapters(String epubPath) {
        List<ChapterContent> chapters = new ArrayList<>();
        
        try (ZipFile zipFile = new ZipFile(new File(epubPath))) {
            // Find the OPF file
            String opfPath = findOPFPath(zipFile);
            logger.info("Found OPF file at: {}", opfPath);
            
            // Parse the spine to get reading order
            List<String> spineFiles = parseSpine(zipFile, opfPath);
            logger.info("Found {} files in reading order", spineFiles.size());
            
            // First, try to find numbered chapters (第一章, 第二章, etc.) in the content
            chapters = extractNumberedChapters(zipFile, spineFiles);
            
            if (!chapters.isEmpty()) {
                logger.info("Found {} numbered chapters (第一章 style)", chapters.size());
            } else {
                // Fall back to file-based extraction with content filtering
                logger.info("No numbered chapters found, using content-based filtering");
                chapters = extractContentBasedChapters(zipFile, spineFiles);
            }
            
        } catch (Exception e) {
            logger.error("Error extracting chapters: {}", e.getMessage(), e);
        }
        
        return chapters;
    }
    
    /**
     * Find the OPF file path from container.xml
     */
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
    
    /**
     * Parse the spine from OPF to get reading order
     */
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
     * Extract all text content from a chapter file
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
                    // Get all text content, preserving structure
                    return body.getTextContent().trim();
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
     * Extract chapters by looking for numbered chapter markers (第一章, 第二章, etc.) in content
     */
    private List<ChapterContent> extractNumberedChapters(ZipFile zipFile, List<String> spineFiles) {
        List<ChapterContent> chapters = new ArrayList<>();
        
        // Get all text content first
        StringBuilder allText = new StringBuilder();
        for (String fileName : spineFiles) {
            String text = extractChapterText(zipFile, fileName);
            if (text.length() > 100) { // Skip very small files
                allText.append(text).append("\n");
            }
        }
        
        String fullText = allText.toString();
        logger.info("Full text length: {} characters", fullText.length());
        
        // Look for numbered chapter patterns, potentially with titles
        java.util.regex.Pattern chapterPattern = java.util.regex.Pattern.compile(
            "第[一二三四五六七八九十〇０-９\\d]+章[\\s　]*([^\\n]*)", 
            java.util.regex.Pattern.MULTILINE
        );
        
        java.util.regex.Matcher matcher = chapterPattern.matcher(fullText);
        List<ChapterMatch> chapterMatches = new ArrayList<>();
        
        // Let's also check for simpler patterns to see what's in the text
        logger.info("Searching for chapter patterns in text...");
        java.util.regex.Pattern simplePattern = java.util.regex.Pattern.compile("第[一二三四五六七八九十]+章");
        java.util.regex.Matcher simpleMatcher = simplePattern.matcher(fullText);
        while (simpleMatcher.find()) {
            logger.info("Found simple chapter marker: '{}' at position {}", simpleMatcher.group(), simpleMatcher.start());
        }
        
        while (matcher.find()) {
            ChapterMatch match = new ChapterMatch();
            String chapterNumber = matcher.group().split("[\\s　]")[0]; // Get just "第一章" part
            String chapterName = matcher.group(1); // Get the title part after the number
            
            // Combine number and name if name exists
            if (chapterName != null && !chapterName.trim().isEmpty()) {
                match.title = chapterNumber + "　" + chapterName.trim();
            } else {
                match.title = chapterNumber;
            }
            
            match.startIndex = matcher.start();
            
            // Only add matches that look like they're in actual content, not table of contents
            // Skip matches that are very early in the text (likely TOC)
            if (match.startIndex > 1000) {
                chapterMatches.add(match);
                logger.info("Found chapter marker: '{}' at position {}", match.title, match.startIndex);
            } else {
                logger.debug("Skipping TOC chapter marker: '{}' at position {}", match.title, match.startIndex);
            }
        }
        
        logger.info("Total chapter matches found: {}", chapterMatches.size());
        
        // If we found numbered chapters, extract content between them
        if (chapterMatches.size() >= 1) {
            for (int i = 0; i < chapterMatches.size(); i++) {
                ChapterMatch current = chapterMatches.get(i);
                
                int contentStart = current.startIndex;
                int contentEnd = (i + 1 < chapterMatches.size()) ? 
                    chapterMatches.get(i + 1).startIndex : fullText.length();
                
                String chapterText = fullText.substring(contentStart, contentEnd).trim();
                
                // Only include if substantial content
                if (chapterText.length() > 1000) {
                    ChapterContent chapter = new ChapterContent(i + 1, current.title.trim());
                    chapter.fullText = chapterText;
                    chapter.length = chapterText.length();
                    chapter.startPosition = contentStart;
                    chapters.add(chapter);
                    
                    logger.info("Extracted numbered chapter: {} ({} characters)", 
                        current.title.trim(), chapterText.length());
                }
            }
        }
        
        return chapters;
    }
    
    /**
     * Extract chapters by filtering content files and detecting actual chapter names
     */
    private List<ChapterContent> extractContentBasedChapters(ZipFile zipFile, List<String> spineFiles) {
        List<ChapterContent> chapters = new ArrayList<>();
        int chapterNumber = 1;
        int totalPosition = 0;
        
        for (String fileName : spineFiles) {
            String chapterText = extractChapterText(zipFile, fileName);
            
            // Skip if too small (likely metadata)
            if (chapterText.length() < 5000) {
                logger.debug("Skipping {} - too small ({} chars)", fileName, chapterText.length());
                continue;
            }
            
            // Skip common non-chapter patterns
            if (isNonChapterContent(chapterText)) {
                logger.debug("Skipping {} - detected as non-chapter content", fileName);
                continue;
            }
            
            // Extract chapter title from content
            String chapterTitle = extractChapterTitle(chapterText);
            if (chapterTitle == null) {
                chapterTitle = "第" + chapterNumber + "章"; // Fallback
            }
            
            ChapterContent chapter = new ChapterContent(chapterNumber, chapterTitle);
            chapter.startPosition = totalPosition;
            chapter.fullText = chapterText;
            chapter.length = chapterText.length();
            
            totalPosition += chapter.length;
            chapters.add(chapter);
            chapterNumber++;
            
            logger.info("Extracted content chapter {}: {} ({} characters)", 
                chapter.chapterNumber, chapter.title, chapter.length);
        }
        
        return chapters;
    }
    
    /**
     * Check if content is likely non-chapter (appendix, references, etc.)
     */
    private boolean isNonChapterContent(String text) {
        String trimmed = text.trim();
        
        // Check for common non-chapter patterns at the beginning
        String[] nonChapterPatterns = {
            "語句の解説",      // Glossary
            "主な参考文献",    // References  
            "あとがき",       // Afterword
            "はじめに",       // Preface
            "目次",          // Table of contents
            "発行者",        // Publisher info
            "本作品の全部または一部を無断で", // Copyright
            "eISBN",        // Electronic ISBN
            "© The Soka Gakkai", // Copyright
            "装画",          // Cover art credits
            "挿画"           // Illustration credits
        };
        
        for (String pattern : nonChapterPatterns) {
            if (trimmed.startsWith(pattern) || trimmed.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extract chapter title from the beginning of chapter content
     */
    private String extractChapterTitle(String text) {
        String[] lines = text.split("\\n");
        
        for (String line : lines) {
            line = line.trim();
            
            // Skip empty lines and very short lines
            if (line.length() < 2) {
                continue;
            }
            
            // Skip lines that look like metadata
            if (line.contains("第") && line.contains("巻")) {
                continue; // Volume info
            }
            if (line.contains("池田大作") || line.contains("聖教新聞社")) {
                continue; // Author/publisher info
            }
            
            // Look for chapter title patterns
            if (line.length() >= 2 && line.length() <= 20) {
                // Could be a chapter title if it's not too long
                // and doesn't look like metadata
                if (!line.contains("http") && !line.contains("ISBN") && 
                    !line.contains("発行") && !line.contains("版")) {
                    return line;
                }
            }
        }
        
        return null; // No clear title found
    }
    
    /**
     * Helper class for chapter matching
     */
    private static class ChapterMatch {
        String title;
        int startIndex;
    }
}