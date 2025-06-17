package com.bungakukensaku.service;

import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Chunk;
import com.bungakukensaku.repository.BookRepository;
import com.bungakukensaku.repository.ChunkRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Temporary service to test database connectivity.
 * This adds sample data on startup for testing.
 * Remove this class once we have real data loading functionality.
 */
@Service
public class DatabaseTestService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTestService.class);
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @PostConstruct
    public void testDatabase() {
        // List current database state
        long bookCount = bookRepository.count();
        long totalChunks = chunkRepository.count();
        
        logger.info("=== データベース状態 ===");
        logger.info("本の数: {}", bookCount);
        logger.info("チャンクの総数: {}", totalChunks);
        
        if (bookCount > 0) {
            logger.info("=== 本のリスト ===");
            bookRepository.findAll().forEach(book -> {
                long chunkCount = chunkRepository.countByBookId(book.getId());
                logger.info("ID: {}, タイトル: {}, 著者: {}, Chunks: {}", 
                    book.getId(), book.getTitle(), book.getAuthor(), chunkCount);
            });
        }
    }
}