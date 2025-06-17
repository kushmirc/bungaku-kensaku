package com.bungakukensaku.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity representing a text chunk from a book.
 * 
 * Each chunk is a searchable unit of text (around 500 tokens) that:
 * - Contains the actual text content
 * - References its source book
 * - Stores vector database ID for similarity search
 * - Includes metadata like chapter and page number
 */
@Entity
@Table(name = "chunks")
public class Chunk {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(name = "pinecone_vector_id")
    private String pineconeVectorId;
    
    @Column(columnDefinition = "TEXT")
    private String chapter;
    
    @Column(name = "chapter_number")
    private Integer chapterNumber;
    
    @Column(name = "chapter_position")
    private Integer chapterPosition; // Character position within chapter
    
    @Column(name = "chapter_percentage")
    private Integer chapterPercentage; // 0-100 percentage through chapter
    
    @Column(name = "page_num")
    private Integer pageNum;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "embedding", columnDefinition = "float[]")
    private float[] embedding;
    
    @Column(name = "uploaded_to_pinecone", nullable = false)
    private boolean uploadedToPinecone = false;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Constructor
    public Chunk() {
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Book getBook() {
        return book;
    }
    
    public void setBook(Book book) {
        this.book = book;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getPineconeVectorId() {
        return pineconeVectorId;
    }
    
    public void setPineconeVectorId(String pineconeVectorId) {
        this.pineconeVectorId = pineconeVectorId;
    }
    
    public String getChapter() {
        return chapter;
    }
    
    public void setChapter(String chapter) {
        this.chapter = chapter;
    }
    
    public Integer getChapterNumber() {
        return chapterNumber;
    }
    
    public void setChapterNumber(Integer chapterNumber) {
        this.chapterNumber = chapterNumber;
    }
    
    public Integer getChapterPosition() {
        return chapterPosition;
    }
    
    public void setChapterPosition(Integer chapterPosition) {
        this.chapterPosition = chapterPosition;
    }
    
    public Integer getChapterPercentage() {
        return chapterPercentage;
    }
    
    public void setChapterPercentage(Integer chapterPercentage) {
        this.chapterPercentage = chapterPercentage;
    }
    
    public Integer getPageNum() {
        return pageNum;
    }
    
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public float[] getEmbedding() {
        return embedding;
    }
    
    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
    
    public boolean isUploadedToPinecone() {
        return uploadedToPinecone;
    }
    
    public void setUploadedToPinecone(boolean uploadedToPinecone) {
        this.uploadedToPinecone = uploadedToPinecone;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}