package com.bungakukensaku.service;

import com.bungakukensaku.model.Author;
import com.bungakukensaku.repository.AuthorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Author entity operations.
 * 
 * Provides business logic for managing Japanese literature authors,
 * including search functionality and data validation.
 */
@Service
public class AuthorService {

    @Autowired
    private AuthorRepository authorRepository;

    /**
     * Get all authors ordered by birth date
     */
    public List<Author> getAllAuthors() {
        return authorRepository.findAllByOrderByBirthDateAsc();
    }

    /**
     * Find author by ID
     */
    public Optional<Author> findById(Long id) {
        return authorRepository.findById(id);
    }

    /**
     * Search for authors by name (searches all name fields)
     */
    public List<Author> searchByName(String searchTerm) {
        return authorRepository.findByNameJapaneseContainingIgnoreCaseOrNameRomajiContainingIgnoreCaseOrNameEnglishContainingIgnoreCase(
            searchTerm, searchTerm, searchTerm);
    }

    /**
     * Find authors by literary period
     */
    public List<Author> findByLiteraryPeriod(String period) {
        return authorRepository.findByLiteraryPeriod(period);
    }

    /**
     * Find living authors (no death date)
     */
    public List<Author> findLivingAuthors() {
        return authorRepository.findByDeathDateIsNull();
    }

    /**
     * Save or update an author
     */
    public Author save(Author author) {
        return authorRepository.save(author);
    }

    /**
     * Delete an author by ID
     */
    public void deleteById(Long id) {
        authorRepository.deleteById(id);
    }

    /**
     * Check if an author exists by any name field
     */
    public boolean existsByName(String nameJapanese, String nameRomaji, String nameEnglish) {
        return authorRepository.findByNameJapanese(nameJapanese).isPresent() ||
               (nameRomaji != null && authorRepository.findByNameRomaji(nameRomaji).isPresent()) ||
               (nameEnglish != null && authorRepository.findByNameEnglish(nameEnglish).isPresent());
    }
}