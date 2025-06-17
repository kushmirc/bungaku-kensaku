package com.bungakukensaku.service;

import com.bungakukensaku.model.Book;
import com.bungakukensaku.model.Author;
import com.bungakukensaku.repository.BookRepository;
import com.bungakukensaku.repository.AuthorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Book entity operations.
 * 
 * Centralizes business logic for managing books in the Japanese literature collection,
 * including CRUD operations, search functionality, and author-book relationships.
 */
@Service
public class BookService {

    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private AuthorRepository authorRepository;

    /**
     * Get all books ordered by creation date
     */
    public List<Book> getAllBooks() {
        return bookRepository.findAllByOrderByCreatedAtAsc();
    }

    /**
     * Find book by ID
     */
    public Optional<Book> findById(Long id) {
        return bookRepository.findById(id);
    }

    /**
     * Search books by title (case insensitive, partial match)
     */
    public List<Book> searchByTitle(String searchTerm) {
        return bookRepository.findByTitleContainingIgnoreCase(searchTerm);
    }

    /**
     * Find books by author name (string-based search)
     */
    public List<Book> findByAuthor(String author) {
        return bookRepository.findByAuthor(author);
    }

    /**
     * Find books by series
     */
    public List<Book> findBySeries(String series) {
        return bookRepository.findBySeries(series);
    }

    /**
     * Find books with no series assigned
     */
    public List<Book> findBooksWithoutSeries() {
        return bookRepository.findBySeriesIsNull();
    }

    /**
     * Find exact book by title
     */
    public Optional<Book> findByTitle(String title) {
        return bookRepository.findByTitle(title);
    }

    /**
     * Save or update a book
     */
    public Book save(Book book) {
        return bookRepository.save(book);
    }

    /**
     * Delete a book by ID
     */
    public void deleteById(Long id) {
        bookRepository.deleteById(id);
    }

    /**
     * Create a new book with author relationship
     */
    public Book createBook(String title, String authorName, Integer year, String series, String s3FilePath) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(authorName);
        book.setYear(year);
        book.setSeries(series);
        book.setS3FilePath(s3FilePath);
        
        // Try to find and link the Author entity
        Optional<Author> author = findAuthorByName(authorName);
        if (author.isPresent()) {
            book.setAuthorEntity(author.get());
        }
        
        return bookRepository.save(book);
    }

    /**
     * Update book with author relationship
     */
    public Book updateBook(Long bookId, String title, String authorName, Integer year, String series, String s3FilePath) {
        Optional<Book> existingBook = bookRepository.findById(bookId);
        if (existingBook.isPresent()) {
            Book book = existingBook.get();
            book.setTitle(title);
            book.setAuthor(authorName);
            book.setYear(year);
            book.setSeries(series);
            book.setS3FilePath(s3FilePath);
            
            // Update author relationship
            Optional<Author> author = findAuthorByName(authorName);
            if (author.isPresent()) {
                book.setAuthorEntity(author.get());
            } else {
                book.setAuthorEntity(null);
            }
            
            return bookRepository.save(book);
        }
        throw new RuntimeException("Book not found with ID: " + bookId);
    }

    /**
     * Link a book to an author entity
     */
    public Book linkBookToAuthor(Long bookId, Long authorId) {
        Optional<Book> book = bookRepository.findById(bookId);
        Optional<Author> author = authorRepository.findById(authorId);
        
        if (book.isPresent() && author.isPresent()) {
            Book bookEntity = book.get();
            bookEntity.setAuthorEntity(author.get());
            return bookRepository.save(bookEntity);
        }
        throw new RuntimeException("Book or Author not found");
    }

    /**
     * Get books by author entity
     */
    public List<Book> getBooksByAuthor(Author author) {
        return author.getBooks();
    }

    /**
     * Check if a book title already exists
     */
    public boolean existsByTitle(String title) {
        return bookRepository.findByTitle(title).isPresent();
    }

    /**
     * Helper method to find author by any name field
     */
    private Optional<Author> findAuthorByName(String authorName) {
        // Try Japanese name first
        Optional<Author> author = authorRepository.findByNameJapanese(authorName);
        if (author.isPresent()) {
            return author;
        }
        
        // Try romaji name
        author = authorRepository.findByNameRomaji(authorName);
        if (author.isPresent()) {
            return author;
        }
        
        // Try English name
        return authorRepository.findByNameEnglish(authorName);
    }

    /**
     * Get total number of books
     */
    public long getTotalBookCount() {
        return bookRepository.count();
    }

    /**
     * Get books grouped by series
     */
    public List<String> getAllSeries() {
        return bookRepository.findAll()
                .stream()
                .map(Book::getSeries)
                .filter(series -> series != null && !series.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }
}