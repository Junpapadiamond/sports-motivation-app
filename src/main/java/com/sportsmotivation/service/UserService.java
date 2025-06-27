package com.sportsmotivation.service;

import com.sportsmotivation.model.User;
import com.sportsmotivation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // User Registration
    public User createUser(String username, String email, String rawPassword) {
        // Validation
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        // Create user with hashed password
        User user = new User(username, email, passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    // User Authentication Support
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    // Update user login timestamp
    public void updateLastLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    // User Preferences Management
    public User updateSportsPreferences(Long userId, String preferences) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setSportsPreferences(preferences);
        return userRepository.save(user);
    }

    // User Profile Updates
    public User updateUserProfile(Long userId, String username, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Check for conflicts with other users
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already taken: " + email);
        }
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }

        user.setUsername(username);
        user.setEmail(email);
        return userRepository.save(user);
    }

    // Password Change
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // Admin/Analytics Methods
    public List<User> getAllActiveUsers() {
        return userRepository.findByIsActiveTrue();
    }

    public List<User> getUsersBySportPreference(String sport) {
        return userRepository.findBySportPreference(sport);
    }

    public List<User> getInactiveUsers(int daysInactive) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysInactive);
        return userRepository.findInactiveUsers(cutoff);
    }

    // Soft Delete
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setIsActive(false);
        userRepository.save(user);
    }

    // Validation Helpers
    public boolean isValidEmail(String email) {
        return email != null && email.contains("@") && !userRepository.existsByEmail(email);
    }

    public boolean isValidUsername(String username) {
        return username != null && username.length() >= 3 && !userRepository.existsByUsername(username);
    }
}