package com.bungakukensaku.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity for tracking search queries and analytics.
 * 
 * This helps us understand:
 * - What users are searching for
 * - Which filters they're using
 * - How many results they're getting
 * - Search patterns over time
 */
@Entity
@Table(name = "search_logs")
public class SearchLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String query;
    
    @Column(name = "book_filter")
    private String bookFilter;
    
    @Column(name = "results_count")
    private Integer resultsCount;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    // Constructor
    public SearchLog() {
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public String getBookFilter() {
        return bookFilter;
    }
    
    public void setBookFilter(String bookFilter) {
        this.bookFilter = bookFilter;
    }
    
    public Integer getResultsCount() {
        return resultsCount;
    }
    
    public void setResultsCount(Integer resultsCount) {
        this.resultsCount = resultsCount;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}