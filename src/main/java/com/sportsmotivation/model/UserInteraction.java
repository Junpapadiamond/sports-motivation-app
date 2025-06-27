package com.sportsmotivation.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_interactions",
        indexes = {
                @Index(name = "idx_user_timestamp", columnList = "user_id, timestamp"),
                @Index(name = "idx_video_timestamp", columnList = "video_id, timestamp")
        })
public class UserInteraction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    // Simple string: "CLICK", "VIEW", "LIKE", "SKIP"
    @Column(name = "interaction_type", nullable = false, length = 20)
    private String interactionType;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    // Optional metadata as simple string - can parse later
    @Column(name = "metadata", length = 500)
    private String metadata; // "watchDuration:120,completionRate:0.8"

    public UserInteraction() {
        this.timestamp = LocalDateTime.now();
    }

    public UserInteraction(Long userId, Long videoId, String interactionType) {
        this();
        this.userId = userId;
        this.videoId = videoId;
        this.interactionType = interactionType;
    }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }

    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}