package com.bungakukensaku.controller;

import com.bungakukensaku.dto.SeriesInfo;
import com.bungakukensaku.model.Book;
import com.bungakukensaku.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HomeController handles the main homepage and navigation
 * 
 * This controller serves the home.html template which contains:
 * - Main search interface
 * - Navigation menu
 * - Feature descriptions
 */
@Controller
public class HomeController {
    
    @Autowired
    private BookRepository bookRepository;
    
    /**
     * Serves the homepage with search interface and dynamic series data
     * 
     * @param model the Spring MVC model to pass data to the template
     * @return the home template name (home.html)
     */
    @GetMapping("/")
    public String home(Model model) {
        List<SeriesInfo> seriesInfoList = getSeriesInfoList();
        model.addAttribute("seriesInfoList", seriesInfoList);
        return "home";
    }
    
    /**
     * Alternative mapping for explicit /home requests
     * 
     * @param model the Spring MVC model to pass data to the template
     * @return the home template name (home.html)
     */
    @GetMapping("/home")
    public String homePage(Model model) {
        List<SeriesInfo> seriesInfoList = getSeriesInfoList();
        model.addAttribute("seriesInfoList", seriesInfoList);
        return "home";
    }
    
    /**
     * Helper method to organize books by series for the homepage
     * 
     * @return List of SeriesInfo objects containing series and book data
     */
    private List<SeriesInfo> getSeriesInfoList() {
        List<Book> allBooks = bookRepository.findAllByOrderByCreatedAtAsc();
        List<SeriesInfo> seriesInfoList = new ArrayList<>();
        
        // Group books by series (including null series)
        Map<String, List<Book>> booksBySeries = allBooks.stream()
                .collect(Collectors.groupingBy(book -> 
                    book.getSeries() != null ? book.getSeries() : "INDIVIDUAL_BOOKS"));
        
        // Convert to SeriesInfo objects
        for (Map.Entry<String, List<Book>> entry : booksBySeries.entrySet()) {
            String seriesName = entry.getKey();
            List<Book> books = entry.getValue();
            
            if ("INDIVIDUAL_BOOKS".equals(seriesName)) {
                // Create individual SeriesInfo for each standalone book
                for (Book book : books) {
                    seriesInfoList.add(new SeriesInfo(book.getTitle(), List.of(book)));
                }
            } else {
                // Create SeriesInfo for actual series
                seriesInfoList.add(new SeriesInfo(seriesName, books));
            }
        }
        
        return seriesInfoList;
    }
    
    /**
     * PDF processing test page
     * For development and testing purposes
     * 
     * @return the pdf-test template name (pdf-test.html)
     */
    @GetMapping("/pdf-test")
    public String pdfTest() {
        return "pdf-test";
    }
    
    /**
     * EPUB processing test page
     * For development and testing purposes
     * 
     * @return the epub-test template name (epub-test.html)
     */
    @GetMapping("/epub-test")
    public String epubTest() {
        return "epub-test";
    }
    
    /**
     * Embedding generation test page
     * For development and testing purposes
     * 
     * @return the embedding-test template name (embedding-test.html)
     */
    @GetMapping("/embedding-test")
    public String embeddingTest() {
        return "embedding-test";
    }
    
    /**
     * EPUB extraction diagnostic page
     * For investigating extraction issues
     * 
     * @return the diagnostic template name (diagnostic.html)
     */
    @GetMapping("/diagnostic")
    public String diagnostic() {
        return "diagnostic";
    }
    
    /**
     * Book migration/reprocessing page
     * For updating books with fixed extraction
     * 
     * @return the migration template name (migration.html)
     */
    @GetMapping("/migration")
    public String migration() {
        return "migration";
    }
    
    /**
     * Chunk viewer page
     * For verifying complete book extraction
     * 
     * @return the chunk-viewer template name (chunk-viewer.html)
     */
    @GetMapping("/chunk-viewer")
    public String chunkViewer() {
        return "chunk-viewer";
    }
    
    /**
     * Overview page showing system description and how-to guide
     * 
     * @return the overview template name (overview.html)
     */
    @GetMapping("/about")
    public String about() {
        return "overview";
    }
    
    /**
     * Alternative mapping for overview page
     * 
     * @return the overview template name (overview.html)
     */
    @GetMapping("/overview")
    public String overview() {
        return "overview";
    }
    
    /**
     * Technical specifications page for demo presentation
     * Shows the technical architecture and components under the hood
     * 
     * @return the features template name (features.html)
     */
    @GetMapping("/features")
    public String features() {
        return "features";
    }
    
    /**
     * Contact page placeholder for demo
     * Shows under construction message
     * 
     * @return the contact template name (contact.html)
     */
    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }
}