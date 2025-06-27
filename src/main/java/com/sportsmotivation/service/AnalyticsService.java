package com.sportsmotivation.service;

import com.sportsmotivation.model.User;
import com.sportsmotivation.model.Video;
import com.sportsmotivation.model.UserInteraction;
import com.sportsmotivation.model.ViewingHistory;
import com.sportsmotivation.model.Recommendation;
import com.sportsmotivation.repository.RecommendationRepository;
import com.sportsmotivation.repository.UserInteractionRepository;
import com.sportsmotivation.repository.UserRepository;
import com.sportsmotivation.repository.VideoRepository;
import com.sportsmotivation.repository.ViewingHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserInteractionRepository interactionRepository;
    private final ViewingHistoryRepository viewingHistoryRepository;
    private final CacheService cacheService;

    @Autowired
    public AnalyticsService(UserRepository userRepository,
                            VideoRepository videoRepository,
                            RecommendationRepository recommendationRepository,
                            UserInteractionRepository interactionRepository,
                            ViewingHistoryRepository viewingHistoryRepository,
                            CacheService cacheService) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.recommendationRepository = recommendationRepository;
        this.interactionRepository = interactionRepository;
        this.viewingHistoryRepository = viewingHistoryRepository;
        this.cacheService = cacheService;
    }

    /**
     * Comprehensive system analytics dashboard
     */
    public Map<String, Object> getSystemAnalytics(int days) {
        Map<String, Object> analytics = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Basic metrics
        analytics.put("totalUsers", userRepository.count());
        analytics.put("activeUsers", userRepository.findByIsActiveTrue().size());
        analytics.put("totalVideos", videoRepository.count());
        analytics.put("totalInteractions", interactionRepository.count());

        // User engagement metrics
        analytics.put("userEngagement", getUserEngagementMetrics(days));

        // Content performance
        analytics.put("contentPerformance", getContentPerformanceMetrics(days));

        // Recommendation system performance
        analytics.put("recommendationPerformance", getRecommendationAnalytics(days));

        // Real-time activity
        analytics.put("realtimeActivity", getRealTimeMetrics());

        // User retention analysis
        analytics.put("userRetention", getUserRetentionAnalysis(days));

        analytics.put("generatedAt", LocalDateTime.now());
        analytics.put("periodDays", days);

        return analytics;
    }

    /**
     * Deep user engagement analysis
     */
    public Map<String, Object> getUserEngagementMetrics(int days) {
        Map<String, Object> engagement = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Average session duration
        List<ViewingHistory> recentViewing = viewingHistoryRepository
                .findByUserIdAndWatchDateAfterOrderByWatchDateDesc(null, since);

        double avgSessionDuration = recentViewing.stream()
                .mapToInt(ViewingHistory::getWatchDurationSeconds)
                .average()
                .orElse(0.0);

        // Completion rate analysis
        double avgCompletionRate = recentViewing.stream()
                .mapToDouble(ViewingHistory::getCompletionRate)
                .average()
                .orElse(0.0);

        // User activity distribution
        Map<String, Long> activityByDay = interactionRepository
                .findAll()
                .stream()
                .filter(interaction -> interaction.getTimestamp().isAfter(since))
                .collect(Collectors.groupingBy(
                        interaction -> interaction.getTimestamp().getDayOfWeek().toString(),
                        Collectors.counting()
                ));

        // High engagement users (top 10%)
        List<Object[]> userEngagementScores = getUserEngagementScores(days);

        engagement.put("averageSessionDurationSeconds", avgSessionDuration);
        engagement.put("averageCompletionRate", avgCompletionRate);
        engagement.put("activityByDayOfWeek", activityByDay);
        engagement.put("topEngagedUsers", userEngagementScores);
        engagement.put("engagementTrend", getEngagementTrend(days));

        return engagement;
    }

    /**
     * Content performance analytics with ML insights
     */
    public Map<String, Object> getContentPerformanceMetrics(int days) {
        Map<String, Object> performance = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Video performance stats
        List<Object[]> videoStats = viewingHistoryRepository.getVideoPerformanceStats(since);

        // Category performance
        Map<String, Object> categoryPerformance = getCategoryPerformanceAnalysis(days);

        // Content quality indicators
        Map<String, Object> qualityMetrics = getContentQualityMetrics(days);

        // Trending content identification
        List<Long> trendingVideoIds = interactionRepository.getTrendingVideoIds(since,
                PageRequest.of(0, 10));

        performance.put("videoPerformanceStats", videoStats);
        performance.put("categoryPerformance", categoryPerformance);
        performance.put("qualityMetrics", qualityMetrics);
        performance.put("trendingVideos", trendingVideoIds);
        performance.put("underperformingContent", identifyUnderperformingContent(days));

        return performance;
    }

    /**
     * AI/ML Recommendation system analytics
     */
    public Map<String, Object> getRecommendationAnalytics(int days) {
        Map<String, Object> analytics = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Algorithm performance comparison
        List<Object[]> clickThroughRates = recommendationRepository.getClickThroughRateByAlgorithm(since);
        List<Object[]> averageScores = recommendationRepository.getAverageScoreByAlgorithm(since);

        // Recommendation effectiveness
        Map<String, Double> algorithmEffectiveness = calculateAlgorithmEffectiveness(clickThroughRates);

        // Diversity metrics
        Map<String, Object> diversityMetrics = calculateRecommendationDiversity(days);

        // User satisfaction indicators
        Map<String, Object> satisfactionMetrics = calculateUserSatisfactionMetrics(days);

        analytics.put("clickThroughRates", clickThroughRates);
        analytics.put("averageScores", averageScores);
        analytics.put("algorithmEffectiveness", algorithmEffectiveness);
        analytics.put("diversityMetrics", diversityMetrics);
        analytics.put("userSatisfaction", satisfactionMetrics);
        analytics.put("recommendationCoverage", calculateRecommendationCoverage(days));

        return analytics;
    }

    /**
     * Real-time system metrics
     */
    public Map<String, Object> getRealTimeMetrics() {
        Map<String, Object> realtime = new HashMap<>();
        LocalDateTime lastHour = LocalDateTime.now().minusHours(1);

        // Active users in last hour
        long activeUsersLastHour = interactionRepository
                .findRecentUserActivity(null, lastHour, PageRequest.of(0, 1000))
                .stream()
                .map(UserInteraction::getUserId)
                .distinct()
                .count();

        // Interactions per minute
        List<UserInteraction> lastHourInteractions = interactionRepository
                .findAll()
                .stream()
                .filter(interaction -> interaction.getTimestamp().isAfter(lastHour))
                .collect(Collectors.toList());

        double interactionsPerMinute = lastHourInteractions.size() / 60.0;

        // Most active categories right now
        Map<String, Long> activeCategoriesNow = getActiveCategoriesRealTime();

        realtime.put("activeUsersLastHour", activeUsersLastHour);
        realtime.put("interactionsPerMinute", interactionsPerMinute);
        realtime.put("activeCategoriesNow", activeCategoriesNow);
        realtime.put("systemLoad", calculateSystemLoad());

        return realtime;
    }

    /**
     * User retention and cohort analysis
     */
    public Map<String, Object> getUserRetentionAnalysis(int days) {
        Map<String, Object> retention = new HashMap<>();

        // Cohort analysis by registration month
        Map<String, Object> cohortAnalysis = performCohortAnalysis();

        // Churn prediction indicators
        List<Long> usersAtRisk = identifyUsersAtRisk(days);

        // Retention by user segments
        Map<String, Double> retentionBySegment = calculateRetentionBySegment(days);

        retention.put("cohortAnalysis", cohortAnalysis);
        retention.put("usersAtRisk", usersAtRisk.size());
        retention.put("retentionBySegment", retentionBySegment);
        retention.put("averageUserLifetimeValue", calculateAverageUserLifetimeValue());

        return retention;
    }

    /**
     * Individual user behavior analysis (for personalization)
     */
    public Map<String, Object> getUserBehaviorProfile(Long userId) {
        Map<String, Object> profile = new HashMap<>();

        // User engagement score
        double engagementScore = calculateUserEngagementScore(userId);

        // Preferred content types
        Map<String, Double> contentPreferences = analyzeUserContentPreferences(userId);

        // Viewing patterns
        Map<String, Object> viewingPatterns = analyzeUserViewingPatterns(userId);

        // Recommendation receptivity
        double recommendationReceptivity = calculateRecommendationReceptivity(userId);

        profile.put("engagementScore", engagementScore);
        profile.put("contentPreferences", contentPreferences);
        profile.put("viewingPatterns", viewingPatterns);
        profile.put("recommendationReceptivity", recommendationReceptivity);
        profile.put("userSegment", classifyUserSegment(userId));

        return profile;
    }

    /**
     * A/B testing framework for recommendation algorithms
     */
    @Async
    public CompletableFuture<Map<String, Object>> runRecommendationABTest(String algorithmA,
                                                                          String algorithmB,
                                                                          int testDays) {
        Map<String, Object> results = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(testDays);

        // Get recommendations from both algorithms
        List<Recommendation> algorithmARecommendations = recommendationRepository
                .findAll()
                .stream()
                .filter(r -> r.getAlgorithmType().equals(algorithmA) && r.getCreatedAt().isAfter(since))
                .collect(Collectors.toList());

        List<Recommendation> algorithmBRecommendations = recommendationRepository
                .findAll()
                .stream()
                .filter(r -> r.getAlgorithmType().equals(algorithmB) && r.getCreatedAt().isAfter(since))
                .collect(Collectors.toList());

        // Calculate performance metrics
        double ctrA = calculateCTR(algorithmARecommendations);
        double ctrB = calculateCTR(algorithmBRecommendations);

        // Statistical significance test (simplified)
        boolean isSignificant = Math.abs(ctrA - ctrB) > 0.05; // 5% threshold

        results.put("algorithmA", algorithmA);
        results.put("algorithmB", algorithmB);
        results.put("ctrA", ctrA);
        results.put("ctrB", ctrB);
        results.put("winner", ctrA > ctrB ? algorithmA : algorithmB);
        results.put("isStatisticallySignificant", isSignificant);
        results.put("testPeriodDays", testDays);

        return CompletableFuture.completedFuture(results);
    }

    // Private helper methods for complex calculations

    private List<Object[]> getUserEngagementScores(int days) {
        // Calculate engagement score for each user
        List<User> users = userRepository.findByIsActiveTrue();

        return users.stream()
                .map(user -> new Object[]{
                        user.getId(),
                        user.getUsername(),
                        calculateUserEngagementScore(user.getId())
                })
                .sorted((a, b) -> Double.compare((Double) b[2], (Double) a[2]))
                .limit(20)
                .collect(Collectors.toList());
    }

    private Map<String, Object> getEngagementTrend(int days) {
        Map<String, Object> trend = new HashMap<>();
        LocalDateTime start = LocalDateTime.now().minusDays(days);

        // Daily engagement metrics
        Map<String, Double> dailyEngagement = new HashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDateTime date = start.plusDays(i);
            double dayEngagement = calculateDailyEngagementScore(date);
            dailyEngagement.put(date.toLocalDate().toString(), dayEngagement);
        }

        trend.put("dailyEngagement", dailyEngagement);
        trend.put("trendDirection", calculateTrendDirection(dailyEngagement));

        return trend;
    }

    private Map<String, Object> getCategoryPerformanceAnalysis(int days) {
        Map<String, Object> analysis = new HashMap<>();

        // Get all categories
        List<Object[]> categoryCounts = videoRepository.countByCategory();

        for (Object[] categoryData : categoryCounts) {
            String category = (String) categoryData[0];

            // Calculate performance metrics for this category
            Map<String, Object> categoryMetrics = calculateCategoryMetrics(category, days);
            analysis.put(category, categoryMetrics);
        }

        return analysis;
    }

    private Map<String, Object> getContentQualityMetrics(int days) {
        Map<String, Object> quality = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // High completion rate content
        List<ViewingHistory> highQualityViews = viewingHistoryRepository
                .findAll()
                .stream()
                .filter(vh -> vh.getWatchDate().isAfter(since) && vh.getCompletionRate() > 0.8)
                .collect(Collectors.toList());

        quality.put("highQualityContentCount", highQualityViews.size());
        quality.put("averageQualityScore", calculateAverageQualityScore(days));
        quality.put("qualityDistribution", calculateQualityDistribution(days));

        return quality;
    }

    private List<Video> identifyUnderperformingContent(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        return videoRepository.findAll()
                .stream()
                .filter(video -> {
                    double avgCompletion = getVideoAverageCompletion(video.getId(), since);
                    return avgCompletion < 0.3; // Less than 30% completion rate
                })
                .limit(10)
                .collect(Collectors.toList());
    }

    private double calculateUserEngagementScore(Long userId) {
        // Multi-factor engagement scoring algorithm
        double baseScore = 0.0;

        // Factor 1: Average completion rate (40% weight)
        Double avgCompletion = viewingHistoryRepository.getAverageCompletionRate(userId);
        if (avgCompletion != null) {
            baseScore += avgCompletion * 0.4;
        }

        // Factor 2: Interaction frequency (30% weight)
        long recentInteractions = interactionRepository
                .findByUserIdAndTimestampAfterOrderByTimestampDesc(
                        userId, LocalDateTime.now().minusDays(7))
                .size();
        double interactionScore = Math.min(recentInteractions / 50.0, 1.0); // Normalize to max 50 interactions
        baseScore += interactionScore * 0.3;

        // Factor 3: Content diversity (20% weight)
        double diversityScore = calculateUserContentDiversity(userId);
        baseScore += diversityScore * 0.2;

        // Factor 4: Session length (10% weight)
        Double avgWatchTime = viewingHistoryRepository.getAverageWatchDuration(userId);
        if (avgWatchTime != null) {
            double sessionScore = Math.min(avgWatchTime / 600.0, 1.0); // Normalize to 10 minutes max
            baseScore += sessionScore * 0.1;
        }

        return Math.min(baseScore, 1.0); // Cap at 1.0
    }

    private double calculateUserContentDiversity(Long userId) {
        List<UserInteraction> interactions = interactionRepository.findByUserIdOrderByTimestampDesc(userId);

        Set<String> categoriesViewed = new HashSet<>();
        for (UserInteraction interaction : interactions) {
            videoRepository.findById(interaction.getVideoId()).ifPresent(video -> {
                categoriesViewed.add(video.getCategory());
            });
        }

        // Diversity score based on number of different categories
        return Math.min(categoriesViewed.size() / 5.0, 1.0); // Normalize to 5 categories max
    }

    private Map<String, Double> calculateAlgorithmEffectiveness(List<Object[]> clickThroughRates) {
        return clickThroughRates.stream()
                .collect(Collectors.toMap(
                        data -> (String) data[0], // algorithm name
                        data -> (Double) data[1]  // CTR
                ));
    }

    private Map<String, Object> calculateRecommendationDiversity(int days) {
        Map<String, Object> diversity = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Calculate category diversity in recommendations
        List<Recommendation> recentRecommendations = recommendationRepository
                .findAll()
                .stream()
                .filter(r -> r.getCreatedAt().isAfter(since))
                .collect(Collectors.toList());

        Map<String, Long> categoryDistribution = new HashMap<>();
        for (Recommendation rec : recentRecommendations) {
            videoRepository.findById(rec.getVideoId()).ifPresent(video -> {
                categoryDistribution.merge(video.getCategory(), 1L, Long::sum);
            });
        }

        // Calculate diversity index (Shannon entropy)
        double totalRecs = recentRecommendations.size();
        double diversityIndex = categoryDistribution.values().stream()
                .mapToDouble(count -> {
                    double probability = count / totalRecs;
                    return -probability * Math.log(probability);
                })
                .sum();

        diversity.put("categoryDistribution", categoryDistribution);
        diversity.put("diversityIndex", diversityIndex);
        diversity.put("uniqueVideosRecommended",
                recentRecommendations.stream().map(Recommendation::getVideoId).distinct().count());

        return diversity;
    }

    private Map<String, Object> calculateUserSatisfactionMetrics(int days) {
        Map<String, Object> satisfaction = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Click-through satisfaction
        List<Recommendation> clickedRecs = recommendationRepository.findClickedRecommendations(null);
        double satisfactionScore = clickedRecs.stream()
                .filter(r -> r.getCreatedAt().isAfter(since))
                .mapToDouble(Recommendation::getScore)
                .average()
                .orElse(0.0);

        // Recommendation-to-completion pipeline
        long recommendationsClicked = clickedRecs.size();
        long recommendationsCompleted = calculateCompletedRecommendations(clickedRecs);

        double completionRate = recommendationsClicked > 0 ?
                (double) recommendationsCompleted / recommendationsClicked : 0.0;

        satisfaction.put("averageSatisfactionScore", satisfactionScore);
        satisfaction.put("recommendationCompletionRate", completionRate);
        satisfaction.put("userFeedbackScore", calculateImplicitFeedbackScore(days));

        return satisfaction;
    }

    private double calculateRecommendationCoverage(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // What percentage of videos are being recommended?
        long totalVideos = videoRepository.count();
        long recommendedVideos = recommendationRepository
                .findAll()
                .stream()
                .filter(r -> r.getCreatedAt().isAfter(since))
                .map(Recommendation::getVideoId)
                .distinct()
                .count();

        return totalVideos > 0 ? (double) recommendedVideos / totalVideos : 0.0;
    }

    private Map<String, Long> getActiveCategoriesRealTime() {
        LocalDateTime lastHour = LocalDateTime.now().minusHours(1);

        Map<String, Long> activeCategories = new HashMap<>();
        List<UserInteraction> recentInteractions = interactionRepository
                .findAll()
                .stream()
                .filter(interaction -> interaction.getTimestamp().isAfter(lastHour))
                .collect(Collectors.toList());

        for (UserInteraction interaction : recentInteractions) {
            videoRepository.findById(interaction.getVideoId()).ifPresent(video -> {
                activeCategories.merge(video.getCategory(), 1L, Long::sum);
            });
        }

        return activeCategories;
    }

    private double calculateSystemLoad() {
        // Simple system load calculation based on interactions per minute
        LocalDateTime lastMinute = LocalDateTime.now().minusMinutes(1);
        long recentInteractions = interactionRepository
                .findAll()
                .stream()
                .filter(interaction -> interaction.getTimestamp().isAfter(lastMinute))
                .count();

        // Normalize to 0-1 scale (assuming 100 interactions/minute is high load)
        return Math.min(recentInteractions / 100.0, 1.0);
    }

    private Map<String, Object> performCohortAnalysis() {
        Map<String, Object> cohorts = new HashMap<>();

        // Group users by registration month
        Map<String, List<User>> userCohorts = userRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(user ->
                        user.getCreatedAt().getYear() + "-" +
                                String.format("%02d", user.getCreatedAt().getMonthValue())
                ));

        // Calculate retention for each cohort
        Map<String, Map<String, Double>> cohortRetention = new HashMap<>();

        for (Map.Entry<String, List<User>> cohort : userCohorts.entrySet()) {
            String cohortMonth = cohort.getKey();
            List<User> cohortUsers = cohort.getValue();

            Map<String, Double> retentionByMonth = new HashMap<>();

            // Calculate retention for each subsequent month
            for (int monthsLater = 1; monthsLater <= 12; monthsLater++) {
                double retentionRate = calculateCohortRetention(cohortUsers, monthsLater);
                retentionByMonth.put("month_" + monthsLater, retentionRate);
            }

            cohortRetention.put(cohortMonth, retentionByMonth);
        }

        cohorts.put("cohortRetention", cohortRetention);
        cohorts.put("cohortSizes", userCohorts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().size()
                )));

        return cohorts;
    }

    private List<Long> identifyUsersAtRisk(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

        return userRepository.findByIsActiveTrue()
                .stream()
                .filter(user -> {
                    // Users who haven't interacted recently
                    boolean hasRecentActivity = interactionRepository
                            .findByUserIdAndTimestampAfterOrderByTimestampDesc(user.getId(), cutoff)
                            .isEmpty();

                    // And have declining engagement
                    double recentEngagement = calculateUserEngagementScore(user.getId());

                    return hasRecentActivity && recentEngagement < 0.3;
                })
                .map(User::getId)
                .collect(Collectors.toList());
    }

    private Map<String, Double> calculateRetentionBySegment(int days) {
        Map<String, Double> retention = new HashMap<>();

        // Segment by user preferences
        List<User> users = userRepository.findByIsActiveTrue();
        Map<String, List<User>> segments = users.stream()
                .collect(Collectors.groupingBy(user ->
                        user.getSportsPreferences() != null ?
                                user.getSportsPreferences().split(",")[0] : "Unknown"
                ));

        for (Map.Entry<String, List<User>> segment : segments.entrySet()) {
            double segmentRetention = calculateSegmentRetentionRate(segment.getValue(), days);
            retention.put(segment.getKey(), segmentRetention);
        }

        return retention;
    }

    private double calculateAverageUserLifetimeValue() {
        // Simplified LTV calculation based on engagement
        return userRepository.findByIsActiveTrue()
                .stream()
                .mapToDouble(user -> calculateUserEngagementScore(user.getId()) * 100) // Scale to monetary value
                .average()
                .orElse(0.0);
    }

    private Map<String, Double> analyzeUserContentPreferences(Long userId) {
        Map<String, Double> preferences = new HashMap<>();

        List<UserInteraction> interactions = interactionRepository.findByUserIdOrderByTimestampDesc(userId);
        Map<String, Long> categoryInteractions = new HashMap<>();

        // Count interactions by category
        for (UserInteraction interaction : interactions) {
            videoRepository.findById(interaction.getVideoId()).ifPresent(video -> {
                categoryInteractions.merge(video.getCategory(), 1L, Long::sum);
            });
        }

        // Convert to preferences (normalize to percentages)
        long totalInteractions = categoryInteractions.values().stream().mapToLong(Long::longValue).sum();

        categoryInteractions.forEach((category, count) -> {
            double preference = totalInteractions > 0 ? (double) count / totalInteractions : 0.0;
            preferences.put(category, preference);
        });

        return preferences;
    }

    private Map<String, Object> analyzeUserViewingPatterns(Long userId) {
        Map<String, Object> patterns = new HashMap<>();

        List<ViewingHistory> history = viewingHistoryRepository.findByUserIdOrderByWatchDateDesc(userId);

        // Peak viewing hours
        Map<Integer, Long> hourlyActivity = history.stream()
                .collect(Collectors.groupingBy(
                        vh -> vh.getWatchDate().getHour(),
                        Collectors.counting()
                ));

        // Viewing session patterns
        double avgSessionLength = history.stream()
                .mapToInt(ViewingHistory::getWatchDurationSeconds)
                .average()
                .orElse(0.0);

        // Binge-watching indicator
        long bingeingSessions = history.stream()
                .filter(vh -> vh.getWatchDurationSeconds() > 1800) // 30+ minutes
                .count();

        patterns.put("peakViewingHours", hourlyActivity);
        patterns.put("averageSessionLength", avgSessionLength);
        patterns.put("bingeWatchingSessions", bingeingSessions);
        patterns.put("preferredContentLength", calculatePreferredContentLength(userId));

        return patterns;
    }

    private double calculateRecommendationReceptivity(Long userId) {
        // How often does user click on recommendations?
        List<Recommendation> userRecommendations = recommendationRepository.findByUserIdOrderByScoreDesc(userId);

        if (userRecommendations.isEmpty()) {
            return 0.0;
        }

        long clickedRecommendations = userRecommendations.stream()
                .filter(r -> Boolean.TRUE.equals(r.getWasClicked()))
                .count();

        return (double) clickedRecommendations / userRecommendations.size();
    }

    private String classifyUserSegment(Long userId) {
        double engagementScore = calculateUserEngagementScore(userId);
        double receptivity = calculateRecommendationReceptivity(userId);

        if (engagementScore > 0.8 && receptivity > 0.6) {
            return "POWER_USER";
        } else if (engagementScore > 0.6) {
            return "ENGAGED_USER";
        } else if (engagementScore > 0.3) {
            return "CASUAL_USER";
        } else {
            return "AT_RISK_USER";
        }
    }

    // Additional helper methods

    private double calculateCTR(List<Recommendation> recommendations) {
        if (recommendations.isEmpty()) {
            return 0.0;
        }

        long clicked = recommendations.stream()
                .filter(r -> Boolean.TRUE.equals(r.getWasClicked()))
                .count();

        return (double) clicked / recommendations.size();
    }

    private double calculateDailyEngagementScore(LocalDateTime date) {
        LocalDateTime startOfDay = date.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<UserInteraction> dayInteractions = interactionRepository
                .findAll()
                .stream()
                .filter(interaction ->
                        interaction.getTimestamp().isAfter(startOfDay) &&
                                interaction.getTimestamp().isBefore(endOfDay))
                .collect(Collectors.toList());

        // Simple engagement calculation based on interaction volume
        return Math.min(dayInteractions.size() / 1000.0, 1.0); // Normalize to 1000 interactions
    }

    private String calculateTrendDirection(Map<String, Double> dailyEngagement) {
        List<Double> values = new ArrayList<>(dailyEngagement.values());
        if (values.size() < 2) {
            return "INSUFFICIENT_DATA";
        }

        // Simple trend calculation
        double firstHalf = values.subList(0, values.size() / 2).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double secondHalf = values.subList(values.size() / 2, values.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        if (secondHalf > firstHalf * 1.1) {
            return "UPWARD";
        } else if (secondHalf < firstHalf * 0.9) {
            return "DOWNWARD";
        } else {
            return "STABLE";
        }
    }

    private Map<String, Object> calculateCategoryMetrics(String category, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Video> categoryVideos = videoRepository.findByCategory(category);

        // Average performance metrics for this category
        double avgViewCount = categoryVideos.stream()
                .mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L)
                .average()
                .orElse(0.0);

        double avgCompletionRate = calculateCategoryAverageCompletion(category, since);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("videoCount", categoryVideos.size());
        metrics.put("averageViewCount", avgViewCount);
        metrics.put("averageCompletionRate", avgCompletionRate);
        metrics.put("popularityTrend", calculateCategoryTrend(category, days));

        return metrics;
    }

    private double calculateAverageQualityScore(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        return viewingHistoryRepository
                .findAll()
                .stream()
                .filter(vh -> vh.getWatchDate().isAfter(since))
                .mapToDouble(ViewingHistory::getCompletionRate)
                .average()
                .orElse(0.0);
    }

    private Map<String, Long> calculateQualityDistribution(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<ViewingHistory> recentViewing = viewingHistoryRepository
                .findAll()
                .stream()
                .filter(vh -> vh.getWatchDate().isAfter(since))
                .collect(Collectors.toList());

        Map<String, Long> distribution = new HashMap<>();
        distribution.put("low_quality", recentViewing.stream().filter(vh -> vh.getCompletionRate() < 0.3).count());
        distribution.put("medium_quality", recentViewing.stream().filter(vh -> vh.getCompletionRate() >= 0.3 && vh.getCompletionRate() < 0.7).count());
        distribution.put("high_quality", recentViewing.stream().filter(vh -> vh.getCompletionRate() >= 0.7).count());

        return distribution;
    }

    private double getVideoAverageCompletion(Long videoId, LocalDateTime since) {
        return viewingHistoryRepository
                .findAll()
                .stream()
                .filter(vh -> vh.getVideoId().equals(videoId) && vh.getWatchDate().isAfter(since))
                .mapToDouble(ViewingHistory::getCompletionRate)
                .average()
                .orElse(0.0);
    }

    private long calculateCompletedRecommendations(List<Recommendation> recommendations) {
        return recommendations.stream()
                .filter(rec -> {
                    // Check if the recommended video was completed (>80% watched)
                    return viewingHistoryRepository
                            .findAll()
                            .stream()
                            .anyMatch(vh ->
                                    vh.getVideoId().equals(rec.getVideoId()) &&
                                            vh.getUserId().equals(rec.getUserId()) &&
                                            vh.getCompletionRate() > 0.8);
                })
                .count();
    }

    private double calculateImplicitFeedbackScore(int days) {
        // Calculate based on replay behavior, skipping patterns, etc.
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<ViewingHistory> recentViewing = viewingHistoryRepository
                .findAll()
                .stream()
                .filter(vh -> vh.getWatchDate().isAfter(since))
                .collect(Collectors.toList());

        double positiveSignals = recentViewing.stream()
                .filter(vh -> vh.getReplayCount() > 0 || vh.getCompletionRate() > 0.8)
                .count();

        return recentViewing.size() > 0 ? positiveSignals / recentViewing.size() : 0.0;
    }

    private double calculateCohortRetention(List<User> cohortUsers, int monthsLater) {
        LocalDateTime retentionCutoff = LocalDateTime.now().minusMonths(monthsLater);

        long retainedUsers = cohortUsers.stream()
                .filter(user -> {
                    // Check if user has activity after the retention cutoff
                    return !interactionRepository
                            .findByUserIdAndTimestampAfterOrderByTimestampDesc(user.getId(), retentionCutoff)
                            .isEmpty();
                })
                .count();

        return cohortUsers.size() > 0 ? (double) retainedUsers / cohortUsers.size() : 0.0;
    }

    private double calculateSegmentRetentionRate(List<User> segmentUsers, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

        long activeUsers = segmentUsers.stream()
                .filter(user -> {
                    return !interactionRepository
                            .findByUserIdAndTimestampAfterOrderByTimestampDesc(user.getId(), cutoff)
                            .isEmpty();
                })
                .count();

        return segmentUsers.size() > 0 ? (double) activeUsers / segmentUsers.size() : 0.0;
    }

    private String calculatePreferredContentLength(Long userId) {
        List<ViewingHistory> userHistory = viewingHistoryRepository.findByUserIdOrderByWatchDateDesc(userId);

        if (userHistory.isEmpty()) {
            return "UNKNOWN";
        }

        double avgWatchTime = userHistory.stream()
                .mapToInt(ViewingHistory::getWatchDurationSeconds)
                .average()
                .orElse(0.0);

        if (avgWatchTime < 300) { // 5 minutes
            return "SHORT";
        } else if (avgWatchTime < 900) { // 15 minutes
            return "MEDIUM";
        } else {
            return "LONG";
        }
    }

    private double calculateCategoryAverageCompletion(String category, LocalDateTime since) {
        List<Video> categoryVideos = videoRepository.findByCategory(category);

        return categoryVideos.stream()
                .mapToDouble(video -> getVideoAverageCompletion(video.getId(), since))
                .average()
                .orElse(0.0);
    }

    private String calculateCategoryTrend(String category, int days) {
        // Simplified trend calculation based on recent interaction volume
        LocalDateTime halfPeriod = LocalDateTime.now().minusDays(days / 2);
        LocalDateTime fullPeriod = LocalDateTime.now().minusDays(days);

        List<Video> categoryVideos = videoRepository.findByCategory(category);
        Set<Long> categoryVideoIds = categoryVideos.stream().map(Video::getId).collect(Collectors.toSet());

        long recentInteractions = interactionRepository
                .findAll()
                .stream()
                .filter(interaction ->
                        interaction.getTimestamp().isAfter(halfPeriod) &&
                                categoryVideoIds.contains(interaction.getVideoId()))
                .count();

        long olderInteractions = interactionRepository
                .findAll()
                .stream()
                .filter(interaction ->
                        interaction.getTimestamp().isAfter(fullPeriod) &&
                                interaction.getTimestamp().isBefore(halfPeriod) &&
                                categoryVideoIds.contains(interaction.getVideoId()))
                .count();

        if (recentInteractions > olderInteractions * 1.2) {
            return "RISING";
        } else if (recentInteractions < olderInteractions * 0.8) {
            return "DECLINING";
        } else {
            return "STABLE";
        }
    }
}