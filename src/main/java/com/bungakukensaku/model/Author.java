package com.bungakukensaku.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity representing a Japanese literature author.
 * 
 * This entity stores comprehensive information about each author including:
 * - Basic information (name in Japanese and English, birth/death dates)
 * - Literary period and biographical details
 * - Relationship to their literary works
 */
@Entity
@Table(name = "authors")
public class Author {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name_japanese", nullable = false)
    private String nameJapanese;
    
    @Column(name = "name_romaji")
    private String nameRomaji;
    
    @Column(name = "name_english")
    private String nameEnglish;
    
    @Column(name = "birth_date")
    private LocalDate birthDate;
    
    @Column(name = "death_date")
    private LocalDate deathDate;
    
    @Column(name = "literary_period")
    private String literaryPeriod;
    
    @Column(columnDefinition = "TEXT")
    private String biography;
    
    @Column(name = "notable_works", columnDefinition = "TEXT")
    private String notableWorks;
    
    // One author has many books
    @OneToMany(mappedBy = "authorEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Book> books = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructor
    public Author() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getNameJapanese() {
        return nameJapanese;
    }
    
    public void setNameJapanese(String nameJapanese) {
        this.nameJapanese = nameJapanese;
    }
    
    public String getNameRomaji() {
        return nameRomaji;
    }
    
    public void setNameRomaji(String nameRomaji) {
        this.nameRomaji = nameRomaji;
    }
    
    public String getNameEnglish() {
        return nameEnglish;
    }
    
    public void setNameEnglish(String nameEnglish) {
        this.nameEnglish = nameEnglish;
    }
    
    public LocalDate getBirthDate() {
        return birthDate;
    }
    
    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
    
    public LocalDate getDeathDate() {
        return deathDate;
    }
    
    public void setDeathDate(LocalDate deathDate) {
        this.deathDate = deathDate;
    }
    
    public String getLiteraryPeriod() {
        return literaryPeriod;
    }
    
    public void setLiteraryPeriod(String literaryPeriod) {
        this.literaryPeriod = literaryPeriod;
    }
    
    public String getBiography() {
        return biography;
    }
    
    public void setBiography(String biography) {
        this.biography = biography;
    }
    
    public String getNotableWorks() {
        return notableWorks;
    }
    
    public void setNotableWorks(String notableWorks) {
        this.notableWorks = notableWorks;
    }
    
    public List<Book> getBooks() {
        return books;
    }
    
    public void setBooks(List<Book> books) {
        this.books = books;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Pre-update callback to update the updatedAt timestamp
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Helper methods for repository queries that expect year as Integer
    public Integer getBirthYear() {
        return birthDate != null ? birthDate.getYear() : null;
    }
    
    public Integer getDeathYear() {
        return deathDate != null ? deathDate.getYear() : null;
    }
    
    // Helper method to get display name (prefer English if available)
    public String getDisplayName() {
        if (nameEnglish != null && !nameEnglish.isEmpty()) {
            return nameEnglish;
        } else if (nameRomaji != null && !nameRomaji.isEmpty()) {
            return nameRomaji;
        } else {
            return nameJapanese;
        }
    }
}