package com.sportsmotivation.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations",
        indexes = {
                @Index(name = "idx_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_score_algorithm", columnList = "score, algorithm_type")
        })
public class Recommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    // AI confidence score (0.0 to 1.0)
    @Column(name = "score", nullable = false)
    private Double score;

    // Which algorithm generated this: "OPENAI_GPT4", "COLLABORATIVE_FILTERING", etc.
    @Column(name = "algorithm_type", nullable = false, length = 50)
    private String algorithmType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // OpenAI reasoning (optional)
    @Column(name = "reasoning", length = 500)
    private String reasoning;

    // Did user actually click this recommendation?
    @Column(name = "was_clicked")
    private Boolean wasClicked = false;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    // Position in recommendation list (1, 2, 3...)
    @Column(name = "recommendation_rank")
    private Integer recommendationRank;

    public Recommendation() {
        this.createdAt = LocalDateTime.now();
        this.wasClicked = false;
    }

    public Recommendation(Long userId, Long videoId, Double score, String algorithmType) {
        this();
        this.userId = userId;
        this.videoId = videoId;
        this.score = score;
        this.algorithmType = algorithmType;
    }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getAlgorithmType() { return algorithmType; }
    public void setAlgorithmType(String algorithmType) { this.algorithmType = algorithmType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public Boolean getWasClicked() { return wasClicked; }
    public void setWasClicked(Boolean wasClicked) { this.wasClicked = wasClicked; }

    public LocalDateTime getClickedAt() { return clickedAt; }
    public void setClickedAt(LocalDateTime clickedAt) { this.clickedAt = clickedAt; }

    public Integer getRecommendationRank() { return recommendationRank; }
    public void setRecommendationRank(Integer recommendationRank) { this.recommendationRank = recommendationRank; }
}