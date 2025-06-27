package com.sportsmotivation.repository;

import com.sportsmotivation.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Basic finders - Spring Data JPA auto-implements these
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    // Find active users only
    List<User> findByIsActiveTrue();

    // Find users by sports preference (simple string matching for now)
    @Query("SELECT u FROM User u WHERE u.sportsPreferences LIKE %:sport%")
    List<User> findBySportPreference(@Param("sport") String sport);

    // Find users created within date range
    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Find users who haven't logged in recently (for analytics)
    @Query("SELECT u FROM User u WHERE u.lastLogin < :cutoffDate OR u.lastLogin IS NULL")
    List<User> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
}