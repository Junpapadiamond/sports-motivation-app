package com.sportsmotivation.repository;

import com.sportsmotivation.model.UserInteraction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {

    // User behavior queries - CRITICAL for OpenAI
    List<UserInteraction> findByUserIdOrderByTimestampDesc(Long userId);

    List<UserInteraction> findByUserIdAndTimestampAfterOrderByTimestampDesc(
            Long userId, LocalDateTime after);

    // Recent user activity (for real-time recommendations)
    @Query("SELECT ui FROM UserInteraction ui WHERE ui.userId = :userId " +
            "AND ui.timestamp >= :since ORDER BY ui.timestamp DESC")
    List<UserInteraction> findRecentUserActivity(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // Video performance analytics
    List<UserInteraction> findByVideoIdOrderByTimestampDesc(Long videoId);

    Long countByVideoIdAndInteractionType(Long videoId, String interactionType);

    // Interaction type analysis
    List<UserInteraction> findByUserIdAndInteractionTypeOrderByTimestampDesc(
            Long userId, String interactionType);

    // User engagement patterns
    @Query("SELECT ui.interactionType, COUNT(ui) FROM UserInteraction ui " +
            "WHERE ui.userId = :userId GROUP BY ui.interactionType")
    List<Object[]> getUserInteractionSummary(@Param("userId") Long userId);

    // Popular videos by interaction type
    @Query("SELECT ui.videoId, COUNT(ui) FROM UserInteraction ui " +
            "WHERE ui.interactionType = :type AND ui.timestamp >= :since " +
            "GROUP BY ui.videoId ORDER BY COUNT(ui) DESC")
    List<Object[]> getMostEngagingVideos(
            @Param("type") String type,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // User activity frequency
    @Query("SELECT DATE(ui.timestamp), COUNT(ui) FROM UserInteraction ui " +
            "WHERE ui.userId = :userId AND ui.timestamp >= :since " +
            "GROUP BY DATE(ui.timestamp) ORDER BY DATE(ui.timestamp)")
    List<Object[]> getUserDailyActivity(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since);

    // Cross-user video popularity
    @Query("SELECT ui.videoId FROM UserInteraction ui " +
            "WHERE ui.interactionType = 'CLICK' AND ui.timestamp >= :since " +
            "GROUP BY ui.videoId ORDER BY COUNT(ui) DESC")
    List<Long> getTrendingVideoIds(@Param("since") LocalDateTime since, Pageable pageable);
}