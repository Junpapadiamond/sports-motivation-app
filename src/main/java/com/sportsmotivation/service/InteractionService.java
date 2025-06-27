package com.sportsmotivation.service;

import com.sportsmotivation.model.UserInteraction;
import com.sportsmotivation.model.ViewingHistory;
import com.sportsmotivation.repository.UserInteractionRepository;
import com.sportsmotivation.repository.ViewingHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
public class InteractionService {

    private final UserInteractionRepository interactionRepository;
    private final ViewingHistoryRepository viewingHistoryRepository;
    private final CacheService cacheService;

    @Autowired
    public InteractionService(UserInteractionRepository interactionRepository,
                              ViewingHistoryRepository viewingHistoryRepository,
                              CacheService cacheService) {
        this.interactionRepository = interactionRepository;
        this.viewingHistoryRepository = viewingHistoryRepository;
        this.cacheService = cacheService;
    }

    // Basic interaction tracking
    @Async
    public CompletableFuture<Void> trackInteraction(Long userId, Long videoId, String interactionType) {
        UserInteraction interaction = new UserInteraction(userId, videoId, interactionType);
        interactionRepository.save(interaction);

        // Clear user behavior cache since new data is available
        cacheService.clearUserBehavior(userId);
        cacheService.clearRecommendations(userId);

        return CompletableFuture.completedFuture(null);
    }

    // Track interaction with metadata
    @Async
    public CompletableFuture<Void> trackInteractionWithMetadata(Long userId, Long videoId,
                                                                String interactionType, String metadata) {
        UserInteraction interaction = new UserInteraction(userId, videoId, interactionType);
        interaction.setMetadata(metadata);
        interactionRepository.save(interaction);

        // Clear caches
        cacheService.clearUserBehavior(userId);
        cacheService.clearRecommendations(userId);

        return CompletableFuture.completedFuture(null);
    }

    // Track detailed viewing history
    @Async
    public CompletableFuture<Void> trackViewingHistory(Long userId, Long videoId,
                                                       Integer watchDurationSeconds,
                                                       Double completionRate) {
        ViewingHistory history = new ViewingHistory(userId, videoId, watchDurationSeconds, completionRate);
        viewingHistoryRepository.save(history);

        // Also create a basic interaction record
        trackInteraction(userId, videoId, "VIEW");

        return CompletableFuture.completedFuture(null);
    }

    // Enhanced viewing tracking with engagement metrics
    @Async
    public CompletableFuture<Void> trackDetailedViewing(Long userId, Long videoId,
                                                        Integer watchDurationSeconds,
                                                        Double completionRate,
                                                        Integer skipCount,
                                                        Integer replayCount) {
        ViewingHistory history = new ViewingHistory(userId, videoId, watchDurationSeconds, completionRate);
        history.setSkipCount(skipCount);
        history.setReplayCount(replayCount);
        viewingHistoryRepository.save(history);

        // Create metadata for interaction
        String metadata = String.format("watchDuration:%d,completionRate:%.2f,skips:%d,replays:%d",
                watchDurationSeconds, completionRate, skipCount, replayCount);
        trackInteractionWithMetadata(userId, videoId, "DETAILED_VIEW", metadata);

        return CompletableFuture.completedFuture(null);
    }

    // Get user interaction history (for OpenAI processing)
    public List<UserInteraction> getUserRecentInteractions(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return interactionRepository.findByUserIdAndTimestampAfterOrderByTimestampDesc(userId, since);
    }

    public List<ViewingHistory> getUserRecentViewing(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return viewingHistoryRepository.findByUserIdAndWatchDateAfterOrderByWatchDateDesc(userId, since);
    }

    // User behavior analysis methods
    public Double getUserAverageCompletionRate(Long userId) {
        return viewingHistoryRepository.getAverageCompletionRate(userId);
    }

    public Double getUserAverageWatchDuration(Long userId) {
        return viewingHistoryRepository.getAverageWatchDuration(userId);
    }

    public List<ViewingHistory> getUserHighEngagementVideos(Long userId, Double minCompletionRate) {
        return viewingHistoryRepository.findHighEngagementVideos(userId, minCompletionRate);
    }

    // Video performance analytics
    public Long getVideoInteractionCount(Long videoId, String interactionType) {
        return interactionRepository.countByVideoIdAndInteractionType(videoId, interactionType);
    }

    // Bulk operations for efficiency
    @Async
    public CompletableFuture<Void> trackMultipleInteractions(List<UserInteraction> interactions) {
        interactionRepository.saveAll(interactions);

        // Clear caches for all affected users
        interactions.stream()
                .map(UserInteraction::getUserId)
                .distinct()
                .forEach(userId -> {
                    cacheService.clearUserBehavior(userId);
                    cacheService.clearRecommendations(userId);
                });

        return CompletableFuture.completedFuture(null);
    }

    // Real-time analytics support
    public List<Object[]> getUserInteractionSummary(Long userId) {
        return interactionRepository.getUserInteractionSummary(userId);
    }

    public List<Long> getTrendingVideoIds(int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return interactionRepository.getTrendingVideoIds(since,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }
}