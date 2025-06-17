package com.bungakukensaku.service;

import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpSession;

/**
 * RateLimitingService manages rate limiting for search requests
 * 
 * This service:
 * - Tracks search counts per session
 * - Enforces session-based limits (100 searches per session)
 * - Provides user-friendly error messages
 */
@Service
public class RateLimitingService {
    
    private static final String SEARCH_COUNT_KEY = "searchCount";
    private static final int MAX_SEARCHES_PER_SESSION = 100;
    
    /**
     * Check if a search request is allowed for this session
     * 
     * @param session HttpSession to track rate limits
     * @return true if search is allowed, false if limit exceeded
     */
    public boolean isSearchAllowed(HttpSession session) {
        Integer searchCount = (Integer) session.getAttribute(SEARCH_COUNT_KEY);
        
        if (searchCount == null) {
            searchCount = 0;
        }
        
        return searchCount < MAX_SEARCHES_PER_SESSION;
    }
    
    /**
     * Increment the search count for this session
     * 
     * @param session HttpSession to update
     */
    public void incrementSearchCount(HttpSession session) {
        Integer searchCount = (Integer) session.getAttribute(SEARCH_COUNT_KEY);
        
        if (searchCount == null) {
            searchCount = 0;
        }
        
        searchCount++;
        session.setAttribute(SEARCH_COUNT_KEY, searchCount);
    }
    
    /**
     * Get the current search count for this session
     * 
     * @param session HttpSession to check
     * @return current search count
     */
    public int getCurrentSearchCount(HttpSession session) {
        Integer searchCount = (Integer) session.getAttribute(SEARCH_COUNT_KEY);
        return searchCount != null ? searchCount : 0;
    }
    
    /**
     * Get remaining searches allowed for this session
     * 
     * @param session HttpSession to check
     * @return remaining search count
     */
    public int getRemainingSearches(HttpSession session) {
        return MAX_SEARCHES_PER_SESSION - getCurrentSearchCount(session);
    }
    
    /**
     * Get the maximum searches allowed per session
     * 
     * @return maximum search limit
     */
    public int getMaxSearchesPerSession() {
        return MAX_SEARCHES_PER_SESSION;
    }
}