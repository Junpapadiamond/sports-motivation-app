package com.sportsmotivation.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "videos")
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // YouTube video ID - this is what we actually need
    @Column(name = "youtube_id", unique = true, nullable = false, length = 20)
    private String youtubeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "thumbnail_url", length = 300)
    private String thumbnailUrl;

    // Simple string for now: "NBA", "NFL", "Soccer"
    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // YouTube stats - these will be refreshed periodically
    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "upload_date")
    private LocalDateTime uploadDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column (name = "status")
    private String status = "ACTIVE"; // "ACTIVE", "PENDING", "BLOCKED", "DELETED"

    public Video() {
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.status = "ACTIVE";
    }

    public Video(String youtubeId, String title, String category) {
        this();
        this.youtubeId = youtubeId;
        this.title = title;
        this.category = category;
    }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getYoutubeId() { return youtubeId; }
    public void setYoutubeId(String youtubeId) { this.youtubeId = youtubeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }

    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }

    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}