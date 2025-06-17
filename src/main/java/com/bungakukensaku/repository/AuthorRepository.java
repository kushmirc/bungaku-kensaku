package com.bungakukensaku.repository;

import com.bungakukensaku.model.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Author entity database operations.
 * 
 * Spring Data JPA automatically provides implementations for:
 * - Basic CRUD operations
 * - Custom query methods based on method names
 */
@Repository
public interface AuthorRepository extends JpaRepository<Author, Long> {
    
    // Find author by Japanese name
    Optional<Author> findByNameJapanese(String nameJapanese);
    
    // Find author by romaji name
    Optional<Author> findByNameRomaji(String nameRomaji);
    
    // Find author by English name
    Optional<Author> findByNameEnglish(String nameEnglish);
    
    // Find authors by literary period
    List<Author> findByLiteraryPeriod(String literaryPeriod);
    
    // Find authors containing search term in any name field
    List<Author> findByNameJapaneseContainingIgnoreCaseOrNameRomajiContainingIgnoreCaseOrNameEnglishContainingIgnoreCase(
        String nameJapanese, String nameRomaji, String nameEnglish);
    
    // Find all authors ordered by birth date
    List<Author> findAllByOrderByBirthDateAsc();
    
    // Find living authors (death date is null)
    List<Author> findByDeathDateIsNull();
    
    // Find authors with birth date not null
    List<Author> findByBirthDateIsNotNull();
}