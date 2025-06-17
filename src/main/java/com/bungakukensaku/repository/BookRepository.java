package com.bungakukensaku.repository;

import com.bungakukensaku.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Book entity database operations.
 * 
 * Spring Data JPA automatically provides implementations for:
 * - Basic CRUD operations
 * - Custom query methods based on method names
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    
    // Find books by series (e.g., for multi-volume works)
    List<Book> findBySeries(String series);
    
    // Find books by author
    List<Book> findByAuthor(String author);
    
    // Find book by title (exact match)
    Optional<Book> findByTitle(String title);
    
    // Find books with title containing search term
    List<Book> findByTitleContainingIgnoreCase(String searchTerm);
    
    // Find all books ordered by creation date
    List<Book> findAllByOrderByCreatedAtAsc();
    
    // Find books with no series assigned (null or empty)
    List<Book> findBySeriesIsNull();
    
    // Find books with empty series string
    List<Book> findBySeriesEquals(String series);
}