package com.bungakukensaku.repository;

import com.bungakukensaku.model.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for SearchLog entity database operations.
 * 
 * Helps track and analyze search patterns for improving
 * the search experience over time.
 */
@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
    
    // Find recent searches
    List<SearchLog> findTop10ByOrderByTimestampDesc();
    
    // Find searches within a time range
    List<SearchLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    // Find most popular search queries
    @Query("SELECT s.query, COUNT(s) as count FROM SearchLog s GROUP BY s.query ORDER BY count DESC")
    List<Object[]> findMostPopularQueries();
    
    // Find searches by book filter
    List<SearchLog> findByBookFilter(String bookFilter);
}