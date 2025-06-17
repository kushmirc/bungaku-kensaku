package com.bungakukensaku.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity representing a book in the Japanese literature collection.
 * 
 * This entity stores metadata about each book including:
 * - Basic info (title, author, year)
 * - Series information (e.g., for multi-volume works)
 * - S3 file path for the PDF/text file
 * - Relationship to text chunks for search
 */
@Entity
@Table(name = "books")
public class Book {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String author;
    
    // Many books can belong to one author (optional relationship)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author authorEntity;
    
    private Integer year;
    
    private String series;
    
    @Column(name = "s3_file_path")
    private String s3FilePath;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // One book has many chunks
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Chunk> chunks = new ArrayList<>();
    
    // Constructor
    public Book() {
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public Integer getYear() {
        return year;
    }
    
    public void setYear(Integer year) {
        this.year = year;
    }
    
    public String getSeries() {
        return series;
    }
    
    public void setSeries(String series) {
        this.series = series;
    }
    
    public String getS3FilePath() {
        return s3FilePath;
    }
    
    public void setS3FilePath(String s3FilePath) {
        this.s3FilePath = s3FilePath;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public List<Chunk> getChunks() {
        return chunks;
    }
    
    public void setChunks(List<Chunk> chunks) {
        this.chunks = chunks;
    }
    
    public Author getAuthorEntity() {
        return authorEntity;
    }
    
    public void setAuthorEntity(Author authorEntity) {
        this.authorEntity = authorEntity;
    }
}