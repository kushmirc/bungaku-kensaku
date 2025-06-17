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
 * Service for parsing EPUB structure to extract chapter and paragraph information
 * 
 * This parser:
 * - Reads EPUB container to find content.opf
 * - Parses the spine to get reading order
 * - Extracts chapter titles from nav/toc
 * - Maintains paragraph numbering within chapters
 */
@Service
public class EPUBStructureParser {
    
    private static final Logger logger = LoggerFactory.getLogger(EPUBStructureParser.class);
    
    public static class ChapterInfo {
        public String title;
        public String fileName;
        public int chapterNumber;
        public String fullText;
        
        public ChapterInfo(String title, String fileName, int chapterNumber) {
            this.title = title;
            this.fileName = fileName;
            this.chapterNumber = chapterNumber;
            this.fullText = "";
        }
    }
    
    /**
     * Parse EPUB structure and extract chapter information
     */
    public List<ChapterInfo> parseEPUBStructure(String epubPath) {
        List<ChapterInfo> chapters = new ArrayList<>();
        
        try (ZipFile zipFile = new ZipFile(new File(epubPath))) {
            // Step 1: Find the OPF file location from META-INF/container.xml
            String opfPath = findOPFPath(zipFile);
            logger.info("Found OPF file at: {}", opfPath);
            
            // Step 2: Parse the OPF to get spine (reading order)
            List<String> spineFiles = parseSpine(zipFile, opfPath);
            logger.info("Found {} files in reading order", spineFiles.size());
            
            // Step 3: Try to get chapter titles from TOC
            List<String> chapterTitles = parseTableOfContents(zipFile, opfPath);
            
            // Step 4: Parse each chapter file for content
            for (int i = 0; i < spineFiles.size(); i++) {
                String fileName = spineFiles.get(i);
                String title = (i < chapterTitles.size()) ? chapterTitles.get(i) : "第" + (i + 1) + "章";
                
                ChapterInfo chapter = new ChapterInfo(title, fileName, i + 1);
                
                // Extract text from the chapter file
                extractChapterText(zipFile, fileName, chapter);
                
                chapters.add(chapter);
                logger.info("Parsed chapter {}: {} with {} characters", 
                    chapter.chapterNumber, chapter.title, chapter.fullText.length());
            }
            
        } catch (Exception e) {
            logger.error("Error parsing EPUB structure: {}", e.getMessage(), e);
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
     * Try to extract chapter titles from navigation
     */
    private List<String> parseTableOfContents(ZipFile zipFile, String opfPath) {
        List<String> titles = new ArrayList<>();
        // This would parse nav.xhtml or toc.ncx
        // For now, return empty list and use default titles
        return titles;
    }
    
    /**
     * Extract full text from a chapter XHTML file
     */
    private void extractChapterText(ZipFile zipFile, String fileName, ChapterInfo chapter) {
        try {
            ZipEntry entry = zipFile.getEntry(fileName);
            if (entry == null) {
                logger.warn("Could not find chapter file: {}", fileName);
                return;
            }
            
            try (InputStream is = zipFile.getInputStream(entry)) {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document doc = builder.parse(is);
                
                // Get the body element and extract all text
                NodeList bodies = doc.getElementsByTagName("body");
                if (bodies.getLength() > 0) {
                    Element body = (Element) bodies.item(0);
                    chapter.fullText = body.getTextContent().trim();
                } else {
                    chapter.fullText = "";
                }
                
                logger.debug("Extracted {} characters from {}", chapter.fullText.length(), fileName);
                
            } catch (Exception e) {
                logger.error("Error parsing XHTML for {}: {}", fileName, e.getMessage());
                chapter.fullText = "";
            }
            
        } catch (Exception e) {
            logger.error("Error extracting text from {}: {}", fileName, e.getMessage());
        }
    }
    
}