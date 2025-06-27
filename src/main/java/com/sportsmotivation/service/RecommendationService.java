package com.sportsmotivation.service;

import com.sportsmotivation.model.Recommendation;
import com.sportsmotivation.model.User;
import com.sportsmotivation.model.UserInteraction;
import com.sportsmotivation.model.Video;
import com.sportsmotivation.model.ViewingHistory;
import com.sportsmotivation.repository.RecommendationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final CacheService cacheService;
    private final UserService userService;
    private final VideoService videoService;
    private final InteractionService interactionService;
    private final OpenAIService openAIService;

    @Value("${recommendation.cache.ttl:30}")
    private int recommendationCacheTTLMinutes;

    @Value("${recommendation.default.count:10}")
    private int defaultRecommendationCount;

    private static final String ALGORITHM_TYPE_OPENAI = "OPENAI_GPT4";
    private static final String ALGORITHM_TYPE_COLLABORATIVE = "COLLABORATIVE_FILTERING";
    private static final String ALGORITHM_TYPE_TRENDING = "TRENDING_FALLBACK";

    @Autowired
    public RecommendationService(RecommendationRepository recommendationRepository,
                                 CacheService cacheService,
                                 UserService userService,
                                 VideoService videoService,
                                 InteractionService interactionService,
                                 OpenAIService openAIService) {
        this.recommendationRepository = recommendationRepository;
        this.cacheService = cacheService;
        this.userService = userService;
        this.videoService = videoService;
        this.interactionService = interactionService;
        this.openAIService = openAIService;
    }

    // Main recommendation method - uses cached results if available
    public List<Recommendation> getRecommendationsForUser(Long userId) {
        // Check cache first
        List<Long> cachedVideoIds = cacheService.getRecommendations(userId);
        if (cachedVideoIds != null && !cachedVideoIds.isEmpty()) {
            return buildRecommendationsFromVideoIds(userId, cachedVideoIds, ALGORITHM_TYPE_OPENAI);
        }

        // Generate new recommendations
        return generateRecommendationsAsync(userId).join();
    }

    // Async recommendation generation
    @Async
    public CompletableFuture<List<Recommendation>> generateRecommendationsAsync(Long userId) {
        try {
            // Get user data
            User user = userService.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // Try OpenAI-powered recommendations first
            List<Recommendation> aiRecommendations = generateAIRecommendations(user);

            if (!aiRecommendations.isEmpty()) {
                // Cache successful AI recommendations
                List<Long> videoIds = aiRecommendations.stream()
                        .map(Recommendation::getVideoId)
                        .collect(Collectors.toList());
                cacheService.cacheRecommendations(userId, videoIds, Duration.ofMinutes(recommendationCacheTTLMinutes));

                return CompletableFuture.completedFuture(aiRecommendations);
            }

            // Fallback to collaborative filtering
            List<Recommendation> collaborativeRecommendations = generateCollaborativeRecommendations(user);

            if (!collaborativeRecommendations.isEmpty()) {
                return CompletableFuture.completedFuture(collaborativeRecommendations);
            }

            // Final fallback to trending content
            List<Recommendation> trendingRecommendations = generateTrendingRecommendations(user);
            return CompletableFuture.completedFuture(trendingRecommendations);

        } catch (Exception e) {
            // Log error and return trending recommendations as fallback
            return CompletableFuture.completedFuture(generateTrendingRecommendations(
                    userService.findById(userId).orElse(null)));
        }
    }

    // OpenAI-powered recommendation generation
    private List<Recommendation> generateAIRecommendations(User user) {
        try {
            // Step 1: Gather user behavior data
            String userBehaviorSummary = getUserBehaviorSummary(user.getId());

            // Step 2: Get available videos for recommendation
            List<Video> availableVideos = getAvailableVideosForRecommendation(user);

            // Step 3: Create OpenAI prompt
            String prompt = buildRecommendationPrompt(user, userBehaviorSummary, availableVideos);

            // Step 4: Call OpenAI API (blocking call for simplicity)
            String aiResponse = openAIService.generateRecommendations(prompt).block();

            if (aiResponse == null || aiResponse.startsWith("ERROR:")) {
                return new ArrayList<>();
            }

            // Step 5: Parse AI response and create recommendations
            return parseAIResponseToRecommendations(user.getId(), aiResponse, availableVideos);

        } catch (Exception e) {
            // Log error and return empty list for fallback
            return new ArrayList<>();
        }
    }

    // Build comprehensive user behavior summary for OpenAI
    private String getUserBehaviorSummary(Long userId) {
        // Check cache first
        String cachedSummary = cacheService.getUserBehavior(userId);
        if (cachedSummary != null) {
            return cachedSummary;
        }

        // Generate new summary
        StringBuilder summary = new StringBuilder();

        // Get recent interactions (last 30 days)
        List<UserInteraction> interactions = interactionService.getUserRecentInteractions(userId, 30);
        List<ViewingHistory> viewingHistory = interactionService.getUserRecentViewing(userId, 30);

        // Analyze interaction patterns
        Map<String, Long> interactionCounts = interactions.stream()
                .collect(Collectors.groupingBy(UserInteraction::getInteractionType, Collectors.counting()));

        // Analyze category preferences
        Map<String, Long> categoryPreferences = new HashMap<>();
        for (UserInteraction interaction : interactions) {
            videoService.getVideoById(interaction.getVideoId()).ifPresent(video -> {
                categoryPreferences.merge(video.getCategory(), 1L, Long::sum);
            });
        }

        // Calculate engagement metrics
        Double avgCompletionRate = interactionService.getUserAverageCompletionRate(userId);
        Double avgWatchDuration = interactionService.getUserAverageWatchDuration(userId);

        // Build summary text
        summary.append("User Behavior Summary:\n");
        summary.append("- Total interactions in last 30 days: ").append(interactions.size()).append("\n");
        summary.append("- Interaction types: ").append(interactionCounts).append("\n");
        summary.append("- Preferred categories: ").append(categoryPreferences).append("\n");
        summary.append("- Average completion rate: ").append(avgCompletionRate != null ? String.format("%.2f", avgCompletionRate) : "N/A").append("\n");
        summary.append("- Average watch duration: ").append(avgWatchDuration != null ? String.format("%.0f seconds", avgWatchDuration) : "N/A").append("\n");

        // Add viewing patterns
        if (!viewingHistory.isEmpty()) {
            List<ViewingHistory> highEngagement = viewingHistory.stream()
                    .filter(vh -> vh.getCompletionRate() >= 0.8)
                    .collect(Collectors.toList());

            summary.append("- High engagement videos: ").append(highEngagement.size()).append(" out of ").append(viewingHistory.size()).append("\n");
        }

        String behaviorSummary = summary.toString();

        // Cache the summary
        cacheService.cacheUserBehavior(userId, behaviorSummary, Duration.ofHours(1));

        return behaviorSummary;
    }

    // Build OpenAI prompt for recommendations
    private String buildRecommendationPrompt(User user, String behaviorSummary, List<Video> availableVideos) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("TASK: Recommend sports videos for user engagement\n\n");
        prompt.append("USER PROFILE:\n");
        prompt.append("- User preferences: ").append(user.getSportsPreferences() != null ? user.getSportsPreferences() : "General sports").append("\n");
        prompt.append("- Account age: ").append(java.time.Period.between(user.getCreatedAt().toLocalDate(), LocalDateTime.now().toLocalDate()).getDays()).append(" days\n\n");

        prompt.append(behaviorSummary).append("\n");

        prompt.append("AVAILABLE VIDEOS (select from these IDs):\n");
        for (int i = 0; i < Math.min(availableVideos.size(), 50); i++) {
            Video video = availableVideos.get(i);
            prompt.append("ID:").append(video.getId())
                    .append(" | ").append(video.getCategory())
                    .append(" | ").append(video.getTitle().substring(0, Math.min(video.getTitle().length(), 60)))
                    .append(" | ").append(video.getDurationSeconds() != null ? video.getDurationSeconds() + "s" : "Unknown duration")
                    .append(" | Views:").append(video.getViewCount() != null ? video.getViewCount() : 0)
                    .append("\n");
        }

        prompt.append("\nINSTRUCTIONS:\n");
        prompt.append("1. Analyze the user's behavior patterns and preferences\n");
        prompt.append("2. Select ").append(defaultRecommendationCount).append(" video IDs that will maximize user engagement\n");
        prompt.append("3. Consider: completion rates, category preferences, video duration, and popularity\n");
        prompt.append("4. Provide variety while respecting user preferences\n");
        prompt.append("5. Return ONLY video IDs in this format: [ID1,ID2,ID3,...] with confidence scores [0.1-1.0]\n");
        prompt.append("Format: ID:score,ID:score,ID:score (example: 123:0.95,456:0.87,789:0.81)\n");

        return prompt.toString();
    }

    // Parse OpenAI response into Recommendation objects
    private List<Recommendation> parseAIResponseToRecommendations(Long userId, String aiResponse, List<Video> availableVideos) {
        List<Recommendation> recommendations = new ArrayList<>();

        try {
            // Extract video IDs and scores from AI response
            // Expected format: "123:0.95,456:0.87,789:0.81"
            String[] parts = aiResponse.replaceAll("[\\[\\]\\s]", "").split(",");

            int rank = 1;
            for (String part : parts) {
                if (part.contains(":")) {
                    String[] idScore = part.split(":");
                    if (idScore.length == 2) {
                        try {
                            Long videoId = Long.parseLong(idScore[0].trim());
                            Double score = Double.parseDouble(idScore[1].trim());

                            // Validate video exists
                            if (availableVideos.stream().anyMatch(v -> v.getId().equals(videoId))) {
                                Recommendation rec = new Recommendation(userId, videoId, score, ALGORITHM_TYPE_OPENAI);
                                rec.setRecommendationRank(rank++);
                                rec.setReasoning("AI-generated based on user behavior analysis");

                                Recommendation savedRec = recommendationRepository.save(rec);
                                recommendations.add(savedRec);
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid format
                        }
                    }
                }
            }

        } catch (Exception e) {
            // If parsing fails, return empty list for fallback
            return new ArrayList<>();
        }

        return recommendations;
    }

    // Collaborative filtering fallback
    private List<Recommendation> generateCollaborativeRecommendations(User user) {
        // Find users with similar preferences
        List<User> similarUsers = userService.getUsersBySportPreference(user.getSportsPreferences());

        // Get their highly-rated videos
        Map<Long, Double> videoScores = new HashMap<>();

        for (User similarUser : similarUsers.stream().limit(10).collect(Collectors.toList())) {
            List<ViewingHistory> theirHistory = interactionService.getUserRecentViewing(similarUser.getId(), 30);

            for (ViewingHistory vh : theirHistory) {
                if (vh.getCompletionRate() >= 0.7) { // High engagement threshold
                    videoScores.merge(vh.getVideoId(), vh.getCompletionRate(), Double::sum);
                }
            }
        }

        // Convert to recommendations
        List<Recommendation> recommendations = videoScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(defaultRecommendationCount)
                .map(entry -> {
                    Recommendation rec = new Recommendation(user.getId(), entry.getKey(),
                            Math.min(entry.getValue(), 1.0), ALGORITHM_TYPE_COLLABORATIVE);
                    return recommendationRepository.save(rec);
                })
                .collect(Collectors.toList());

        return recommendations;
    }

    // Trending content fallback
    private List<Recommendation> generateTrendingRecommendations(User user) {
        String preferredCategory = user != null && user.getSportsPreferences() != null ?
                user.getSportsPreferences().split(",")[0] : "NBA";

        List<Video> trendingVideos = videoService.getPopularVideosByCategory(preferredCategory, defaultRecommendationCount);

        List<Recommendation> recommendations = new ArrayList<>();
        int rank = 1;

        for (Video video : trendingVideos) {
            Recommendation rec = new Recommendation(user != null ? user.getId() : 0L,
                    video.getId(), 0.5, ALGORITHM_TYPE_TRENDING);
            rec.setRecommendationRank(rank++);
            rec.setReasoning("Popular content in preferred category");

            recommendations.add(recommendationRepository.save(rec));
        }

        return recommendations;
    }

    // Get available videos for recommendation (exclude recently watched)
    private List<Video> getAvailableVideosForRecommendation(User user) {
        // Get user's recent viewing history
        List<ViewingHistory> recentViewing = interactionService.getUserRecentViewing(user.getId(), 7);
        Set<Long> recentlyWatchedIds = recentViewing.stream()
                .map(ViewingHistory::getVideoId)
                .collect(Collectors.toSet());

        // Get all videos, excluding recently watched
        List<Video> allVideos = videoService.getAllVideos();

        return allVideos.stream()
                .filter(video -> !recentlyWatchedIds.contains(video.getId()))
                .collect(Collectors.toList());
    }

    // Helper method to build recommendations from video IDs
    private List<Recommendation> buildRecommendationsFromVideoIds(Long userId, List<Long> videoIds, String algorithmType) {
        List<Recommendation> recommendations = new ArrayList<>();

        for (int i = 0; i < videoIds.size(); i++) {
            Long videoId = videoIds.get(i);
            Recommendation rec = new Recommendation(userId, videoId, 0.8, algorithmType);
            rec.setRecommendationRank(i + 1);
            recommendations.add(rec);
        }

        return recommendations;
    }

    // Mark recommendation as clicked (for performance tracking)
    public void markRecommendationClicked(Long userId, Long videoId) {
        recommendationRepository.markAsClicked(userId, videoId, LocalDateTime.now());

        // Clear cache to regenerate fresh recommendations
        cacheService.clearRecommendations(userId);
    }

    // Force refresh recommendations
    public List<Recommendation> refreshRecommendations(Long userId) {
        // Clear all caches
        cacheService.clearRecommendations(userId);
        cacheService.clearUserBehavior(userId);

        // Generate new recommendations
        return generateRecommendationsAsync(userId).join();
    }

    // Get recommendation performance analytics
    public Map<String, Object> getRecommendationPerformance(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Object[]> clickThroughRates = recommendationRepository.getClickThroughRateByAlgorithm(since);
        List<Object[]> averageScores = recommendationRepository.getAverageScoreByAlgorithm(since);

        Map<String, Object> performance = new HashMap<>();
        performance.put("clickThroughRates", clickThroughRates);
        performance.put("averageScores", averageScores);
        performance.put("periodDays", days);
        performance.put("generatedAt", LocalDateTime.now());

        return performance;
    }

    // Cleanup old recommendations
    @Async
    public void cleanupOldRecommendations(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        recommendationRepository.deleteOldRecommendations(cutoffDate);
    }
}