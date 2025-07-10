package com.bungakukensaku.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

@Controller
public class BookFileController {
    
    private static final Logger logger = LoggerFactory.getLogger(BookFileController.class);
    
    @GetMapping("/books/**")
    public ResponseEntity<byte[]> serveBookFile(HttpServletRequest request) throws IOException {
        try {
            // Get the path after /books/
            String requestURI = request.getRequestURI();
            String path = requestURI.substring(requestURI.indexOf("/books/"));
            
            logger.info("Original request URI: {}", requestURI);
            
            // URL decode the path (this converts %E5%86%85 back to 内)
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.toString());
            logger.info("Decoded path: {}", decodedPath);
            
            // Normalize Unicode to NFC (composed form) - this converts く+゙ to ぐ
            String normalizedPath = Normalizer.normalize(decodedPath, Normalizer.Form.NFC);
            logger.info("Normalized path: {}", normalizedPath);
            
            // Construct the resource path
            String resourcePath = "/static" + normalizedPath;
            logger.info("Looking for resource at: {}", resourcePath);
            
            // Try to load the resource
            ClassPathResource resource = new ClassPathResource(resourcePath);
            
            if (!resource.exists()) {
                logger.error("Resource not found: {}", resourcePath);
                // Try with NFD (decomposed form) as fallback
                String decomposedPath = Normalizer.normalize(decodedPath, Normalizer.Form.NFD);
                String altResourcePath = "/static" + decomposedPath;
                logger.info("Trying alternative path: {}", altResourcePath);
                resource = new ClassPathResource(altResourcePath);
                
                if (!resource.exists()) {
                    logger.error("Resource not found with either normalization");
                    return ResponseEntity.notFound().build();
                }
            }
            
            logger.info("Resource found, serving file");
            
            // Read the file
            byte[] content = resource.getInputStream().readAllBytes();
            
            // Return with proper headers
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + "; charset=UTF-8")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(content);
                    
        } catch (Exception e) {
            logger.error("Error serving book file", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}