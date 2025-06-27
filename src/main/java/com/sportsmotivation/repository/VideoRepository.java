package com.sportsmotivation.repository;

import com.sportsmotivation.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    // Basic finders
    Optional<Video> findByYoutubeId(String youtubeId);

    boolean existsByYoutubeId(String youtubeId);

    // Category-based queries (core feature)
    List<Video> findByCategory(String category);

    Page<Video> findByCategory(String category, Pageable pageable);

    // Multiple categories
    List<Video> findByCategoryIn(List<String> categories);

    // Popular content queries
    @Query("SELECT v FROM Video v WHERE v.category = :category ORDER BY v.viewCount DESC")
    List<Video> findTopByCategory(@Param("category") String category, Pageable pageable);

    @Query("SELECT v FROM Video v ORDER BY v.viewCount DESC")
    List<Video> findMostPopular(Pageable pageable);

    // Recent content
    List<Video> findByUploadDateAfterOrderByUploadDateDesc(LocalDateTime date);

    // Content freshness queries
    @Query("SELECT v FROM Video v WHERE v.lastUpdated < :cutoffDate")
    List<Video> findStaleVideos(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Duration-based queries (for user preferences)
    List<Video> findByDurationSecondsBetween(Integer minDuration, Integer maxDuration);

    // Search functionality
    @Query("SELECT v FROM Video v WHERE v.title LIKE %:keyword% OR v.description LIKE %:keyword%")
    List<Video> findByKeyword(@Param("keyword") String keyword);

    // Analytics queries
    @Query("SELECT v.category, COUNT(v) FROM Video v GROUP BY v.category")
    List<Object[]> countByCategory();
}