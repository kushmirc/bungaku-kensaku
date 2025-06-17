package com.bungakukensaku.dto;

import com.bungakukensaku.model.Book;
import java.util.List;

/**
 * DTO for organizing series and book information for the homepage
 */
public class SeriesInfo {
    private String seriesName;
    private List<Book> books;
    private boolean isSingleBook; // true if this "series" is actually just one book
    
    public SeriesInfo(String seriesName, List<Book> books) {
        this.seriesName = seriesName;
        this.books = books;
        this.isSingleBook = books.size() == 1;
    }
    
    // Getters and setters
    public String getSeriesName() {
        return seriesName;
    }
    
    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }
    
    public List<Book> getBooks() {
        return books;
    }
    
    public void setBooks(List<Book> books) {
        this.books = books;
        this.isSingleBook = books.size() == 1;
    }
    
    public boolean isSingleBook() {
        return isSingleBook;
    }
    
    public void setSingleBook(boolean singleBook) {
        isSingleBook = singleBook;
    }
}