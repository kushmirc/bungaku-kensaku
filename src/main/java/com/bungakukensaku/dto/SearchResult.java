package com.bungakukensaku.dto;

/**
 * Data Transfer Object representing a search result
 */
public class SearchResult {
    private String title;
    private String content;
    private String bookSource;
    private int chapterNumber;
    private float relevanceScore;
    
    // Constructors
    public SearchResult() {}
    
    public SearchResult(String title, String content, String bookSource, 
                       int chapterNumber, float relevanceScore) {
        this.title = title;
        this.content = content;
        this.bookSource = bookSource;
        this.chapterNumber = chapterNumber;
        this.relevanceScore = relevanceScore;
    }
    
    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getBookSource() { return bookSource; }
    public void setBookSource(String bookSource) { this.bookSource = bookSource; }
    
    public int getChapterNumber() { return chapterNumber; }
    public void setChapterNumber(int chapterNumber) { this.chapterNumber = chapterNumber; }
    
    public float getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(float relevanceScore) { this.relevanceScore = relevanceScore; }
}