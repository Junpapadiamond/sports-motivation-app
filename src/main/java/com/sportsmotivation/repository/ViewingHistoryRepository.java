package com.sportsmotivation.repository;

import com.sportsmotivation.model.ViewingHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ViewingHistoryRepository extends JpaRepository<ViewingHistory, Long> {

    // User viewing patterns - CRITICAL for OpenAI
    List<ViewingHistory> findByUserIdOrderByWatchDateDesc(Long userId);

    List<ViewingHistory> findByUserIdAndWatchDateAfterOrderByWatchDateDesc(
            Long userId, LocalDateTime after);

    // High engagement content
    @Query("SELECT vh FROM ViewingHistory vh WHERE vh.userId = :userId " +
            "AND vh.completionRate >= :minCompletionRate ORDER BY vh.completionRate DESC")
    List<ViewingHistory> findHighEngagementVideos(
            @Param("userId") Long userId,
            @Param("minCompletionRate") Double minCompletionRate);

    // User preferences analysis
    @Query("SELECT AVG(vh.completionRate) FROM ViewingHistory vh WHERE vh.userId = :userId")
    Double getAverageCompletionRate(@Param("userId") Long userId);

    @Query("SELECT AVG(vh.watchDurationSeconds) FROM ViewingHistory vh WHERE vh.userId = :userId")
    Double getAverageWatchDuration(@Param("userId") Long userId);

    // Video performance analytics
    @Query("SELECT vh.videoId, AVG(vh.completionRate), COUNT(vh) FROM ViewingHistory vh " +
            "WHERE vh.watchDate >= :since GROUP BY vh.videoId ORDER BY AVG(vh.completionRate) DESC")
    List<Object[]> getVideoPerformanceStats(@Param("since") LocalDateTime since);

    // User behavior insights
    List<ViewingHistory> findByUserIdAndReplayCountGreaterThan(Long userId, Integer minReplays);

    @Query("SELECT vh FROM ViewingHistory vh WHERE vh.userId = :userId " +
            "AND vh.skipCount = 0 ORDER BY vh.watchDate DESC")
    List<ViewingHistory> findNoSkipVideos(@Param("userId") Long userId, Pageable pageable);
}