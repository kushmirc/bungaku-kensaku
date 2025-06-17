package com.bungakukensaku.dto;

/**
 * Data Transfer Object representing a search result item for the view
 */
public class SearchResultItem {
    private Float score;
    private String content;
    private String bookTitle;
    private String chapter;
    private Long chunkId;
    private Long bookId;
    private String contextSummary;
    private String relevanceExplanation;
    private String sourceReference;
    
    // Constructors
    public SearchResultItem() {}
    
    public SearchResultItem(Float score, String content, String bookTitle, String chapter, Long chunkId, Long bookId) {
        this.score = score;
        this.content = content;
        this.bookTitle = bookTitle;
        this.chapter = chapter;
        this.chunkId = chunkId;
        this.bookId = bookId;
    }
    
    // Getters and setters
    public Float getScore() { return score; }
    public void setScore(Float score) { this.score = score; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    
    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }
    
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    
    public String getContextSummary() { return contextSummary; }
    public void setContextSummary(String contextSummary) { this.contextSummary = contextSummary; }
    
    public String getRelevanceExplanation() { return relevanceExplanation; }
    public void setRelevanceExplanation(String relevanceExplanation) { this.relevanceExplanation = relevanceExplanation; }
    
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
}