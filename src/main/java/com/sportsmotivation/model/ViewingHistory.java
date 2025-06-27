package com.sportsmotivation.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "viewing_history",
        indexes = {
                // User activity queries (most common)
                @Index(name = "idx_user_watch", columnList = "user_id, watch_date"),
                @Index(name = "idx_user_completion", columnList = "user_id, completion_rate"),
                @Index(name = "idx_user_video", columnList = "user_id, video_id"),

        })
public class ViewingHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Column(name = "watch_date", nullable = false)
    private LocalDateTime watchDate;

    // How long they actually watched (in seconds)
    @Column(name = "watch_duration_seconds", nullable = false)
    private Integer watchDurationSeconds;

    // Percentage completed (0.0 to 1.0)
    @Column(name = "completion_rate", nullable = false)
    private Double completionRate;

    // Did they watch it multiple times?
    @Column(name = "replay_count")
    private Integer replayCount = 0;

    // Did they skip around or watch straight through?
    @Column(name = "skip_count")
    private Integer skipCount = 0;

    // Peak engagement moment (seconds into video)
    @Column(name = "max_engagement_second")
    private Integer maxEngagementSecond;

    public ViewingHistory() {
        this.watchDate = LocalDateTime.now();
        this.replayCount = 0;
        this.skipCount = 0;
    }

    public ViewingHistory(Long userId, Long videoId, Integer watchDurationSeconds, Double completionRate) {
        this();
        this.userId = userId;
        this.videoId = videoId;
        this.watchDurationSeconds = watchDurationSeconds;
        this.completionRate = completionRate;
    }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }

    public LocalDateTime getWatchDate() { return watchDate; }
    public void setWatchDate(LocalDateTime watchDate) { this.watchDate = watchDate; }

    public Integer getWatchDurationSeconds() { return watchDurationSeconds; }
    public void setWatchDurationSeconds(Integer watchDurationSeconds) { this.watchDurationSeconds = watchDurationSeconds; }

    public Double getCompletionRate() { return completionRate; }
    public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }

    public Integer getReplayCount() { return replayCount; }
    public void setReplayCount(Integer replayCount) { this.replayCount = replayCount; }

    public Integer getSkipCount() { return skipCount; }
    public void setSkipCount(Integer skipCount) { this.skipCount = skipCount; }

    public Integer getMaxEngagementSecond() { return maxEngagementSecond; }
    public void setMaxEngagementSecond(Integer maxEngagementSecond) { this.maxEngagementSecond = maxEngagementSecond; }
}