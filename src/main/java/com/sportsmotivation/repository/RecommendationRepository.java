package com.sportsmotivation.repository;

import com.sportsmotivation.model.Recommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    // Get active recommendations for user
    List<Recommendation> findByUserIdOrderByScoreDesc(Long userId);

    List<Recommendation> findByUserIdAndCreatedAtAfterOrderByScoreDesc(
            Long userId, LocalDateTime after);

    // Track click-through rates
    @Query("SELECT r FROM Recommendation r WHERE r.userId = :userId " +
            "AND r.wasClicked = true ORDER BY r.clickedAt DESC")
    List<Recommendation> findClickedRecommendations(@Param("userId") Long userId);

    // Algorithm performance analysis
    @Query("SELECT r.algorithmType, AVG(CASE WHEN r.wasClicked THEN 1.0 ELSE 0.0 END) " +
            "FROM Recommendation r WHERE r.createdAt >= :since GROUP BY r.algorithmType")
    List<Object[]> getClickThroughRateByAlgorithm(@Param("since") LocalDateTime since);

    @Query("SELECT r.algorithmType, AVG(r.score) FROM Recommendation r " +
            "WHERE r.createdAt >= :since GROUP BY r.algorithmType")
    List<Object[]> getAverageScoreByAlgorithm(@Param("since") LocalDateTime since);

    // Mark recommendation as clicked
    @Modifying
    @Transactional
    @Query("UPDATE Recommendation r SET r.wasClicked = true, r.clickedAt = :clickTime " +
            "WHERE r.userId = :userId AND r.videoId = :videoId AND r.wasClicked = false")
    int markAsClicked(@Param("userId") Long userId,
                      @Param("videoId") Long videoId,
                      @Param("clickTime") LocalDateTime clickTime);

    // Cleanup old recommendations
    @Modifying
    @Transactional
    @Query("DELETE FROM Recommendation r WHERE r.createdAt < :cutoffDate")
    int deleteOldRecommendations(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Performance insights
    @Query("SELECT r.videoId, COUNT(r), AVG(CASE WHEN r.wasClicked THEN 1.0 ELSE 0.0 END) " +
            "FROM Recommendation r WHERE r.createdAt >= :since " +
            "GROUP BY r.videoId ORDER BY AVG(CASE WHEN r.wasClicked THEN 1.0 ELSE 0.0 END) DESC")
    List<Object[]> getMostSuccessfulRecommendations(@Param("since") LocalDateTime since, Pageable pageable);
}