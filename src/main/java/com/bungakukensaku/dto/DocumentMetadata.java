package com.bungakukensaku.dto;

/**
 * Data Transfer Object representing document metadata
 */
public class DocumentMetadata {
    private String title;
    private String author;
    private int pageCount;
    
    // Constructors
    public DocumentMetadata() {}
    
    public DocumentMetadata(String title, String author, int pageCount) {
        this.title = title;
        this.author = author;
        this.pageCount = pageCount;
    }
    
    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
}