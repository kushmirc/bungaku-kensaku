package com.bungakukensaku.controller;

import com.bungakukensaku.dto.SeriesInfo;
import com.bungakukensaku.model.Book;
import com.bungakukensaku.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin controller for managing book and series relationships.
 * Provides interface for:
 * - Viewing all books and their series assignments
 * - Filtering books by series or unassigned status
 * - Assigning books to series
 * - Managing series names
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private BookService bookService;

    /**
     * Display the main admin interface
     */
    @GetMapping
    public String adminPage(@RequestParam(required = false) String filter, Model model) {
        List<Book> books;
        String currentFilter = "all";

        if (filter == null || filter.equals("all")) {
            books = bookService.getAllBooks();
            currentFilter = "all";
        } else if (filter.equals("unassigned")) {
            books = bookService.findBooksWithoutSeries();
            currentFilter = "unassigned";
        } else {
            // Filter by specific series
            books = bookService.findBySeries(filter);
            currentFilter = filter;
        }

        // Get all unique series names for the dropdown
        List<String> allSeries = bookService.getAllSeries();

        // Create series info with book counts for management section
        List<SeriesInfo> seriesInfoList = allSeries.stream()
                .map(seriesName -> {
                    List<Book> seriesBooks = bookService.findBySeries(seriesName);
                    return new SeriesInfo(seriesName, seriesBooks);
                })
                .collect(Collectors.toList());

        model.addAttribute("books", books);
        model.addAttribute("allSeries", allSeries);
        model.addAttribute("seriesInfoList", seriesInfoList);
        model.addAttribute("currentFilter", currentFilter);
        model.addAttribute("totalBooks", bookService.getTotalBookCount());
        model.addAttribute("unassignedCount", bookService.findBooksWithoutSeries().size());
        model.addAttribute("seriesCount", allSeries.size());

        return "admin";
    }

    /**
     * Assign a book to a series
     */
    @PostMapping("/books/{id}/assign-series")
    public String assignSeries(@PathVariable Long id, 
                             @RequestParam String seriesName,
                             RedirectAttributes redirectAttributes) {
        try {
            Book book = bookService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Book not found"));
            
            book.setSeries(seriesName.trim());
            bookService.save(book);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Book \"" + book.getTitle() + "\" assigned to series \"" + seriesName + "\"");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to assign series: " + e.getMessage());
        }
        
        return "redirect:/admin";
    }

    /**
     * Remove a book from its series (set series to null)
     */
    @PostMapping("/books/{id}/remove-series")
    public String removeSeries(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Book book = bookService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Book not found"));
            
            String oldSeries = book.getSeries();
            book.setSeries(null);
            bookService.save(book);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Book \"" + book.getTitle() + "\" removed from series \"" + oldSeries + "\"");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to remove series: " + e.getMessage());
        }
        
        return "redirect:/admin";
    }


    /**
     * Remove a series completely and unassign all books from it
     */
    @PostMapping("/series/{seriesName}/delete")
    public String deleteSeries(@PathVariable String seriesName, RedirectAttributes redirectAttributes) {
        try {
            // Find all books assigned to this series
            List<Book> booksInSeries = bookService.findBySeries(seriesName);
            
            if (booksInSeries.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "シリーズ \"" + seriesName + "\" が見つかりません。");
                return "redirect:/admin";
            }
            
            // Unassign all books from this series
            booksInSeries.forEach(book -> book.setSeries(null));
            // Save each book individually
            for (Book book : booksInSeries) {
                bookService.save(book);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "シリーズ \"" + seriesName + "\" を削除し、" + booksInSeries.size() + "冊の書籍を未割り当てに戻しました。");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to delete series: " + e.getMessage());
        }
        
        return "redirect:/admin";
    }

    /**
     * Bulk assign multiple books to a series
     */
    @PostMapping("/books/bulk-assign")
    public String bulkAssignSeries(@RequestParam List<Long> bookIds, 
                                 @RequestParam String seriesName,
                                 RedirectAttributes redirectAttributes) {
        try {
            // Update each book individually  
            int updatedCount = 0;
            for (Long bookId : bookIds) {
                var book = bookService.findById(bookId).orElse(null);
                if (book != null) {
                    book.setSeries(seriesName.trim());
                    bookService.save(book);
                    updatedCount++;
                }
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    updatedCount + " books assigned to series \"" + seriesName + "\"");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Failed to bulk assign series: " + e.getMessage());
        }
        
        return "redirect:/admin";
    }
}