package com.bungakukensaku.controller;

import com.bungakukensaku.service.SearchService;
import com.bungakukensaku.service.PineconeService;
import com.bungakukensaku.service.EmbeddingService;
import com.bungakukensaku.service.AISummaryService;
import com.bungakukensaku.service.RateLimitingService;
import com.bungakukensaku.service.EPUBRubyPreservingExtractor;
import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Chunk;
import com.bungakukensaku.repository.BookRepository;
import com.bungakukensaku.repository.ChunkRepository;
import com.bungakukensaku.dto.SearchResultItem;
import com.bungakukensaku.dto.SeriesInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SearchController handles all search-related requests
 * 
 * This controller manages:
 * - Search form submissions
 * - Search result display
 * - Search filtering and pagination
 */
@Controller
public class SearchController {
    
    @Autowired
    private SearchService searchService;
    
    @Autowired
    private PineconeService pineconeService;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @Autowired
    private AISummaryService aiSummaryService;
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private RateLimitingService rateLimitingService;
    
    /**
     * Handles search form submissions from the homepage
     * 
     * @param query the search query from the user
     * @param searchScope the scope of search ("all" or "specific")
     * @param request HttpServletRequest to access all form parameters
     * @param model Spring Model to pass data to the view
     * @return the search results template
     */
    @PostMapping("/search")
    public String search(@RequestParam("query") String query, 
                        @RequestParam(value = "searchScope", defaultValue = "all") String searchScope,
                        HttpServletRequest request,
                        Model model) {
        try {
            // Add the query to the model so we can display it back to user
            model.addAttribute("query", query);
            
            // Add searchScope to model to preserve selection
            model.addAttribute("searchScope", searchScope);
            
            // Debug: Log all received parameters
            System.out.println("=== SEARCH FORM PARAMETERS ===");
            System.out.println("Query: " + query);
            System.out.println("SearchScope: " + searchScope);
            request.getParameterNames().asIterator().forEachRemaining(paramName -> {
                String[] values = request.getParameterValues(paramName);
                System.out.println(paramName + " = " + String.join(", ", values));
            });
            System.out.println("=== END PARAMETERS ===");
            
            if (query == null || query.trim().isEmpty()) {
                model.addAttribute("message", "検索クエリを入力してください。");
                // Explicitly set empty results to clear any previous search
                model.addAttribute("results", Collections.emptyList());
                model.addAttribute("resultCount", null);
                // Clear searched books info when showing help text
                model.addAttribute("searchedBooks", null);
                // Include series info for chips
                List<SeriesInfo> seriesInfoList = getSeriesInfoList();
                model.addAttribute("seriesInfoList", seriesInfoList);
                return "home";
            }
            
            // Check rate limiting
            HttpSession session = request.getSession();
            if (!rateLimitingService.isSearchAllowed(session)) {
                int remaining = rateLimitingService.getRemainingSearches(session);
                int total = rateLimitingService.getMaxSearchesPerSession();
                model.addAttribute("message", 
                    String.format("セッションあたりの検索制限に達しました（%d/%d回）。新しいセッションを開始してください。", 
                                  total - remaining, total));
                // Clear any previous search results
                model.addAttribute("results", Collections.emptyList());
                model.addAttribute("resultCount", null);
                model.addAttribute("searchedBooks", null);
                // Include series info for chips
                List<SeriesInfo> seriesInfoList = getSeriesInfoList();
                model.addAttribute("seriesInfoList", seriesInfoList);
                return "home";
            }
            
            // Increment search count early to prevent concurrent requests
            rateLimitingService.incrementSearchCount(session);
            
            // Generate embedding for the search query
            List<String> queryTexts = Collections.singletonList(query.trim());
            List<float[]> queryEmbeddings = embeddingService.generateEmbeddings(queryTexts);
            
            if (queryEmbeddings.isEmpty()) {
                model.addAttribute("message", "検索処理中にエラーが発生しました。");
                // Clear any previous search results
                model.addAttribute("searchedBooks", null);
                // Include series info for chips
                List<SeriesInfo> seriesInfoList = getSeriesInfoList();
                model.addAttribute("seriesInfoList", seriesInfoList);
                return "home";
            }
            
            // Convert to List<Float> for Pinecone
            List<Float> queryVector = new ArrayList<>();
            for (float val : queryEmbeddings.get(0)) {
                queryVector.add(val);
            }
            
            // Build search filter based on user selections and get selected book info
            Map<String, Object> filterResult = buildSearchFilterWithBookInfo(searchScope, request);
            Map<String, Object> searchFilter = (Map<String, Object>) filterResult.get("filter");
            List<String> searchedBookNames = (List<String>) filterResult.get("bookNames");
            
            // Pass the searched book names to the view
            if (searchedBookNames != null && !searchedBookNames.isEmpty()) {
                model.addAttribute("searchedBooks", searchedBookNames);
            }
            
            // Search Pinecone for similar vectors with filtering
            List<PineconeService.SearchResult> searchResults = pineconeService.query(queryVector, 10, searchFilter);
            
            // Convert results to search result objects
            List<SearchResultItem> results = new ArrayList<>();
            int resultIndex = 0;
            for (PineconeService.SearchResult result : searchResults) {
                Map<String, Object> metadata = result.getMetadata();
                
                SearchResultItem item = new SearchResultItem();
                item.setScore(result.getScore());
                
                // Get raw content from metadata
                String rawContent = (String) metadata.get("content");
                
                // Extract the most relevant excerpt for top 5 results only (to manage API costs)
                String contentToDisplay;
                if (resultIndex < 5) {
                    // Use AI to extract relevant excerpt for top results
                    String relevantExcerpt = aiSummaryService.extractRelevantExcerpt(rawContent, query);
                    contentToDisplay = relevantExcerpt;
                } else {
                    // Use full content for results beyond top 5
                    contentToDisplay = rawContent;
                }
                
                // Clean up the content to start with first full sentence
                String cleanedContent = cleanChunkForDisplay(contentToDisplay);
                
                // Convert ruby markup to HTML for display
                String contentWithRuby = EPUBRubyPreservingExtractor.convertToHtmlRuby(cleanedContent);
                item.setContent(contentWithRuby);
                
                resultIndex++;
                
                item.setBookTitle((String) metadata.get("bookTitle"));
                String chapter = (String) metadata.get("chapter");
                // Convert ruby format for chapter titles if needed
                if (chapter != null) {
                    chapter = EPUBRubyPreservingExtractor.convertToHtmlRuby(chapter);
                }
                item.setChapter(chapter);
                
                // Get chunk ID and fetch full chunk if needed
                Object chunkIdObj = metadata.get("chunkId");
                if (chunkIdObj instanceof Number) {
                    Long chunkId = ((Number) chunkIdObj).longValue();
                    Optional<Chunk> chunk = chunkRepository.findById(chunkId);
                    if (chunk.isPresent()) {
                        item.setChunkId(chunkId);
                        item.setBookId(chunk.get().getBook().getId());
                        item.setAuthor(chunk.get().getBook().getAuthor());
                        
                        // Generate detailed source reference
                        try {
                            String sourceRef = generateSourceReference(chunk.get());
                            item.setSourceReference(sourceRef);
                            System.out.println("Generated source reference for chunk " + chunkId + ": " + sourceRef);
                            System.out.println("DEBUG: Final sourceReference in item: " + item.getSourceReference());
                        } catch (Exception e) {
                            System.err.println("Error generating source reference for chunk " + chunkId + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                
                results.add(item);
            }
            
            // Generate AI summaries for the top results (limit to 5 for cost efficiency)
            int summaryLimit = Math.min(results.size(), 5);
            for (int i = 0; i < summaryLimit; i++) {
                SearchResultItem item = results.get(i);
                try {
                    AISummaryService.SearchResultSummary summary = 
                        aiSummaryService.generateSummary(item, query);
                    item.setContextSummary(summary.getContextSummary());
                    item.setRelevanceExplanation(summary.getRelevanceExplanation());
                } catch (Exception e) {
                    // Log error but continue with other results
                    System.err.println("Error generating summary for result " + i + ": " + e.getMessage());
                }
            }
            
            model.addAttribute("results", results);
            model.addAttribute("resultCount", results.size());
            
        } catch (Exception e) {
            model.addAttribute("message", "検索処理中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Always include the series info list for the book selection chips
        List<SeriesInfo> seriesInfoList = getSeriesInfoList();
        model.addAttribute("seriesInfoList", seriesInfoList);
        
        return "home";
    }
    
    /**
     * Handles GET requests to /search (for bookmarking search URLs)
     * 
     * @param query optional query parameter
     * @param model Spring Model to pass data to the view
     * @return the search results template
     */
    @GetMapping("/search")
    public String searchGet(@RequestParam(value = "query", required = false) String query, 
                           Model model,
                           HttpServletRequest request) {
        if (query != null && !query.trim().isEmpty()) {
            return search(query, "all", request, model);
        }
        
        // If no query, redirect to home
        return "redirect:/";
    }
    
    /**
     * Clean chunk content for display by starting with the first complete sentence.
     * This improves readability by avoiding mid-sentence starts.
     * 
     * @param content Raw chunk content
     * @return Cleaned content starting with first complete sentence
     */
    private String cleanChunkForDisplay(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        // Find the first occurrence of Japanese sentence endings
        int firstSentenceEnd = -1;
        for (char c : new char[]{'。', '！', '？'}) {
            int pos = content.indexOf(c);
            if (pos != -1 && (firstSentenceEnd == -1 || pos < firstSentenceEnd)) {
                firstSentenceEnd = pos;
            }
        }
        
        // If we find a sentence ending, start from the next sentence
        if (firstSentenceEnd != -1 && firstSentenceEnd < content.length() - 1) {
            String remaining = content.substring(firstSentenceEnd + 1).trim();
            
            // Make sure we have substantial content remaining
            if (remaining.length() > 50) {
                return remaining;
            }
        }
        
        // If no good sentence break found, return original content
        return content;
    }
    
    /**
     * Generate a detailed source reference showing Book → Chapter → Position.
     * This helps users locate the passage in their physical copy.
     * 
     * @param chunk The chunk containing the search result
     * @return Formatted source reference
     */
    private String generateSourceReference(Chunk chunk) {
        try {
            // Get basic information
            String bookTitle = chunk.getBook().getTitle();
            String chapter = chunk.getChapter();
            Integer chapterPercentage = chunk.getChapterPercentage();
            
            System.out.println("DEBUG: Book title = " + bookTitle + ", Chapter = " + chapter + 
                             ", Percentage = " + chapterPercentage);
            
            if (chapter == null || chapter.isEmpty()) {
                System.out.println("DEBUG: No chapter info, returning book title only");
                return bookTitle; // Just return book title if no chapter info
            }
            
            // Clean up excessive spaces in chapter names (including ideographic spaces)
            chapter = chapter.replaceAll("[\\s　]+", " ").trim();
            
            // Process chapter title - special handling for 人間革命
            String formattedChapter;
            if (bookTitle.contains("人間革命")) {
                // For 人間革命, use parentheses for furigana instead of ruby tags
                formattedChapter = formatHumanRevolutionChapter(chapter);
            } else {
                // For other books, convert to ruby HTML
                formattedChapter = EPUBRubyPreservingExtractor.convertToHtmlRuby(chapter);
            }
            
            // Use percentage if available
            String locationRef;
            if (chapterPercentage != null) {
                // Format percentage as ranges for more natural reading
                int rangeStart = (chapterPercentage / 10) * 10; // Round down to nearest 10
                int rangeEnd = rangeStart + 10;
                if (rangeStart == 0) {
                    locationRef = "冒頭付近"; // Near the beginning
                } else if (rangeEnd >= 100) {
                    locationRef = "終盤付近"; // Near the end
                } else {
                    locationRef = String.format("約%d-%d%%付近", rangeStart, rangeEnd);
                }
            } else {
                // Fallback if no percentage
                locationRef = "位置不明";
            }
            
            // Format: Book → Chapter → Location
            return String.format("%s → %s → %s", bookTitle, formattedChapter, locationRef);
            
        } catch (Exception e) {
            // Fallback to basic reference if anything goes wrong
            return chunk.getBook().getTitle() + (chunk.getChapter() != null ? " → " + chunk.getChapter() : "");
        }
    }
    
    /**
     * Format chapter titles for 人間革命 series.
     * Handles both volumes with different furigana formats:
     * - Volume 2: {kanji|furigana} → kanji（furigana） (has ruby tags)
     * - Volume 1: 開かい　拓たく　者しゃ → 開拓者（かいたくしゃ） (no ruby tags)
     * 
     * @param chapter Original chapter title
     * @return Formatted chapter title with parentheses
     */
    private String formatHumanRevolutionChapter(String chapter) {
        if (chapter == null || chapter.isEmpty()) {
            return chapter;
        }
        
        // Check if chapter has ruby format {kanji|furigana}
        if (chapter.contains("{") && chapter.contains("|") && chapter.contains("}")) {
            // Volume 2 style: Has ruby tags - convert to parentheses
            return chapter.replaceAll("\\{([^|]+)\\|([^}]+)\\}", "$1（$2）");
        } else {
            // Volume 1 style: No ruby tags - convert old spaced format
            return convertOldSpacedFuriganaFormat(chapter);
        }
    }
    
    /**
     * Convert old spaced furigana format to parentheses format.
     * Example: 開かい　拓たく　者しゃ → 開拓者（かいたくしゃ）
     * 
     * @param text Text in old spaced format
     * @return Text with parentheses format
     */
    private String convertOldSpacedFuriganaFormat(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        StringBuilder kanji = new StringBuilder();
        StringBuilder furigana = new StringBuilder();
        
        // Split by spaces (both regular space and ideographic space)
        String[] segments = text.split("[\\s　]+");
        
        for (String segment : segments) {
            if (segment.trim().isEmpty()) continue;
            
            // Process segments that have kanji followed by hiragana
            // Pattern: one or more kanji characters followed by hiragana
            if (segment.matches("^[一-龯]+[あ-ん]+$")) {
                // Find where kanji ends and hiragana begins
                int kanjiEnd = 0;
                for (int i = 0; i < segment.length(); i++) {
                    char c = segment.charAt(i);
                    if (c >= 'あ' && c <= 'ん') {
                        kanjiEnd = i;
                        break;
                    }
                }
                
                if (kanjiEnd > 0) {
                    kanji.append(segment.substring(0, kanjiEnd));
                    furigana.append(segment.substring(kanjiEnd));
                }
            }
        }
        
        // If we successfully extracted both kanji and furigana, format with parentheses
        if (kanji.length() > 0 && furigana.length() > 0) {
            return kanji.toString() + "（" + furigana.toString() + "）";
        }
        
        // If we couldn't parse it properly, return original text
        return text;
    }
    
    /**
     * Build Pinecone metadata filter and collect book information based on user's selections.
     * This filters the vector search to only include chunks from selected books.
     * 
     * @param searchScope "all" for global search, "specific" for filtered search
     * @param request HttpServletRequest containing form parameters
     * @return Map containing "filter" (Pinecone filter) and "bookNames" (list of selected book names)
     */
    private Map<String, Object> buildSearchFilterWithBookInfo(String searchScope, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        // If searching all books, no filter needed
        if ("all".equals(searchScope)) {
            result.put("filter", null);
            result.put("bookNames", null);
            return result;
        }
        
        // Build the filter and collect book IDs
        Map<String, Object> filter = buildSearchFilter(searchScope, request);
        
        // If no filter, no books were selected
        if (filter == null) {
            result.put("filter", null);
            result.put("bookNames", null);
            return result;
        }
        
        // Get the book IDs from the filter
        List<Long> bookIds = (List<Long>) filter.get("bookId");
        if (bookIds != null && !bookIds.isEmpty()) {
            // Look up book names
            List<String> bookNames = new ArrayList<>();
            for (Long bookId : bookIds) {
                Book book = bookRepository.findById(bookId).orElse(null);
                if (book != null) {
                    bookNames.add(book.getTitle());
                }
            }
            result.put("bookNames", bookNames);
        }
        
        result.put("filter", filter);
        return result;
    }
    
    /**
     * Build Pinecone metadata filter based on user's book/series selections.
     * This filters the vector search to only include chunks from selected books.
     * 
     * @param searchScope "all" for global search, "specific" for filtered search
     * @param request HttpServletRequest containing form parameters
     * @return Map representing Pinecone metadata filter, null for no filtering
     */
    private Map<String, Object> buildSearchFilter(String searchScope, HttpServletRequest request) {
        // If searching all books, no filter needed
        if ("all".equals(searchScope)) {
            return null;
        }
        
        // Collect selected book IDs from form parameters
        List<Long> selectedBookIds = new ArrayList<>();
        
        // Get all parameter names to find book selections
        request.getParameterNames().asIterator().forEachRemaining(paramName -> {
            if (paramName.startsWith("book_")) {
                // Extract book ID from parameter name like "book_123"
                try {
                    String bookIdStr = paramName.substring(5); // Remove "book_" prefix
                    Long bookId = Long.parseLong(bookIdStr);
                    String[] values = request.getParameterValues(paramName);
                    // Only add if checkbox was checked (has a value)
                    if (values != null && values.length > 0) {
                        selectedBookIds.add(bookId);
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid book ID formats
                    System.err.println("Invalid book ID format in parameter: " + paramName);
                }
            }
        });
        
        // Handle series selections - when "series_X" is "all", include all books in that series
        request.getParameterNames().asIterator().forEachRemaining(paramName -> {
            if (paramName.startsWith("series_")) {
                String[] values = request.getParameterValues(paramName);
                if (values != null && values.length > 0 && "all".equals(values[0])) {
                    // User selected "all volumes" for this series
                    // Extract series name and find all books in that series
                    String seriesName = paramName.substring(7); // Remove "series_" prefix
                    
                    // TODO: Add logic to get all book IDs for this series
                    // For now, we'll rely on individual book selections
                    System.out.println("User selected all volumes for series: " + seriesName);
                }
            }
        });
        
        // If no books selected, return null (search all)
        if (selectedBookIds.isEmpty()) {
            return null;
        }
        
        // Build Pinecone filter: bookId IN [selectedBookIds]
        // Pass the list directly - PineconeService will handle the $in operator structure
        Map<String, Object> filter = new HashMap<>();
        filter.put("bookId", selectedBookIds);
        
        System.out.println("Built search filter for books: " + selectedBookIds);
        return filter;
    }
    
    /**
     * Helper method to organize books by series for the homepage
     * (Duplicated from HomeController to ensure consistency)
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
    
}