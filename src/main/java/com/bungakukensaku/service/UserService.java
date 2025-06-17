package com.bungakukensaku.service;

import com.bungakukensaku.model.User;
import com.bungakukensaku.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Value("${demo.user.username}")
    private String demoUsername;
    
    @Value("${demo.user.password}")
    private String demoPassword;
    
    @PostConstruct
    public void initializeDefaultUser() {
        if (!userRepository.existsByUsername(demoUsername)) {
            User demoUser = new User(demoUsername, passwordEncoder.encode(demoPassword));
            userRepository.save(demoUser);
            System.out.println("Demo user created: " + demoUsername);
        } else {
            System.out.println("Demo user already exists: " + demoUsername);
        }
    }
    
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}