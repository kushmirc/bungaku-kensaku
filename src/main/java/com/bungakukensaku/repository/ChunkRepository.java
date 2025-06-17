package com.bungakukensaku.repository;

import com.bungakukensaku.model.Chunk;
import com.bungakukensaku.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for Chunk entity database operations.
 * 
 * Handles retrieval of text chunks for search results
 * and vector database synchronization.
 */
@Repository
public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    
    // Find all chunks for a specific book
    List<Chunk> findByBookId(Long bookId);
    
    // Find chunks by Pinecone vector IDs (for search results)
    List<Chunk> findByPineconeVectorIdIn(List<String> vectorIds);
    
    // Find chunks by book and chapter
    List<Chunk> findByBookIdAndChapter(Long bookId, String chapter);
    
    // Find chunks by book and chapter, ordered by ID (preserves text order)
    List<Chunk> findByBookAndChapterOrderById(Book book, String chapter);
    
    // Custom query to find chunks with their book information
    @Query("SELECT c FROM Chunk c JOIN FETCH c.book WHERE c.pineconeVectorId IN :vectorIds")
    List<Chunk> findByVectorIdsWithBook(@Param("vectorIds") List<String> vectorIds);
    
    // Count chunks for a book
    Long countByBookId(Long bookId);
}