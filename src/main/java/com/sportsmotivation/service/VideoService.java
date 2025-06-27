package com.sportsmotivation.service;

import com.sportsmotivation.model.Video;
import com.sportsmotivation.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class VideoService {

    private final VideoRepository videoRepository;
    private final CacheService cacheService;

    @Value("${youtube.api.key:demo-key}")
    private String youtubeApiKey;

    @Value("${youtube.api.quota.daily-limit:10000}")
    private long dailyQuotaLimit;

    private static final List<String> SPORTS_CATEGORIES = Arrays.asList(
            "NBA", "NFL", "Soccer", "Tennis", "Baseball", "Basketball", "Football"
    );

    @Autowired
    public VideoService(VideoRepository videoRepository,
                        CacheService cacheService) {
        this.videoRepository = videoRepository;
        this.cacheService = cacheService;
    }

    // Get videos by category with caching
    public List<Video> getVideosByCategory(String category) {
        // Check cache first
        List<Long> cachedVideoIds = cacheService.getTrendingVideos(category);
        if (cachedVideoIds != null && !cachedVideoIds.isEmpty()) {
            return videoRepository.findAllById(cachedVideoIds);
        }

        // Fallback to database
        List<Video> videos = videoRepository.findByCategory(category);

        // Cache the results
        List<Long> videoIds = videos.stream().map(Video::getId).collect(Collectors.toList());
        cacheService.cacheTrendingVideos(category, videoIds, Duration.ofHours(2));

        return videos;
    }

    public Page<Video> getVideosByCategoryPaged(String category, Pageable pageable) {
        return videoRepository.findByCategory(category, pageable);
    }

    // Get video by YouTube ID with caching
    public Optional<Video> getVideoByYoutubeId(String youtubeId) {
        // Check cache first
        Video cachedVideo = cacheService.getVideoMetadata(youtubeId, Video.class);
        if (cachedVideo != null) {
            return Optional.of(cachedVideo);
        }

        // Check database
        Optional<Video> video = videoRepository.findByYoutubeId(youtubeId);

        // Cache if found
        video.ifPresent(v -> cacheService.cacheVideoMetadata(youtubeId, v, Duration.ofHours(6)));

        return video;
    }

    // Search videos with keyword
    public List<Video> searchVideos(String keyword) {
        return videoRepository.findByKeyword(keyword);
    }

    // Get popular videos by category
    public List<Video> getPopularVideosByCategory(String category, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return videoRepository.findTopByCategory(category, pageable);
    }

    // Get most popular videos overall
    public List<Video> getMostPopularVideos(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return videoRepository.findMostPopular(pageable);
    }

    // Get trending videos for recommendation engine
    public List<Video> getTrendingVideosForRecommendation(String category, int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Video> recentVideos = videoRepository.findByUploadDateAfterOrderByUploadDateDesc(since);

        return recentVideos.stream()
                .filter(v -> category == null || v.getCategory().equals(category))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // Analytics support methods
    public List<Object[]> getVideoCategoryStats() {
        return videoRepository.countByCategory();
    }

    public List<Video> getVideosByDuration(Integer minSeconds, Integer maxSeconds) {
        return videoRepository.findByDurationSecondsBetween(minSeconds, maxSeconds);
    }

    // Manual content management methods
    public Video addVideoManually(String youtubeId, String title, String category) {
        if (videoRepository.existsByYoutubeId(youtubeId)) {
            throw new IllegalArgumentException("Video already exists: " + youtubeId);
        }

        Video video = new Video(youtubeId, title, category);
        // For now, set basic metadata - YouTube API integration will be added later
        video.setDescription("Sports highlight video");
        video.setThumbnailUrl("https://img.youtube.com/vi/" + youtubeId + "/maxresdefault.jpg");
        video.setDurationSeconds(300); // Default 5 minutes
        video.setViewCount(0L);
        video.setLikeCount(0L);
        video.setUploadDate(LocalDateTime.now());

        Video savedVideo = videoRepository.save(video);
        cacheService.cacheVideoMetadata(youtubeId, savedVideo, Duration.ofHours(24));

        return savedVideo;
    }

    public void removeVideo(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoId));

        // Clear from cache
        cacheService.delete("video:metadata:" + video.getYoutubeId());
        cacheService.delete("trending:" + video.getCategory());

        videoRepository.delete(video);
    }

    // Simplified content refresh for now (YouTube API integration will be added later)
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void refreshSportsContent() {
        // For now, just refresh stale video data
        refreshStaleVideoData();
    }

    // Refresh video statistics for stale videos
    @Async
    public CompletableFuture<Void> refreshStaleVideoData() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        List<Video> staleVideos = videoRepository.findStaleVideos(cutoffDate);

        for (Video video : staleVideos) {
            // For now, just update the timestamp - YouTube API enrichment will be added later
            video.setLastUpdated(LocalDateTime.now());
            videoRepository.save(video);

            // Update cache
            cacheService.cacheVideoMetadata(video.getYoutubeId(), video, Duration.ofHours(24));
        }

        return CompletableFuture.completedFuture(null);
    }

    // Async method to fetch sports highlights (placeholder for YouTube API integration)
    @Async
    public CompletableFuture<List<Video>> fetchSportsHighlights(String category, int maxResults) {
        // Placeholder implementation - will integrate with YouTube API later
        List<Video> existingVideos = videoRepository.findByCategory(category);

        // For now, return existing videos
        List<Video> results = existingVideos.stream()
                .limit(maxResults)
                .collect(Collectors.toList());

        return CompletableFuture.completedFuture(results);
    }

    // Get all available categories
    public List<String> getAvailableCategories() {
        return new ArrayList<>(SPORTS_CATEGORIES);
    }

    // Get recent videos
    public List<Video> getRecentVideos(int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Video> recentVideos = videoRepository.findByUploadDateAfterOrderByUploadDateDesc(since);

        return recentVideos.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    // Utility method to get video by ID
    public Optional<Video> getVideoById(Long videoId) {
        return videoRepository.findById(videoId);
    }

    // Bulk video operations
    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public long getTotalVideoCount() {
        return videoRepository.count();
    }

    public long getVideoCountByCategory(String category) {
        return videoRepository.findByCategory(category).size();
    }
}