package com.sportsmotivation.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    private final WebClient openaiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.model:gpt-4.1}")
    private String model;

    @Value("${openai.recommendation.max-tokens:500}")
    private int maxTokens;

    @Value("${openai.recommendation.temperature:0.7}")
    private double temperature;

    @Autowired
    public OpenAIService(@Qualifier("openaiWebClient") WebClient openaiWebClient,
                         ObjectMapper objectMapper) {
        this.openaiWebClient = openaiWebClient;
        this.objectMapper = objectMapper;
    }

    public Mono<String> generateRecommendations(String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(
                        new ChatMessage("system", "You are an AI sports content recommendation expert. Analyze user behavior and recommend videos that will maximize engagement."),
                        new ChatMessage("user", prompt)
                ),
                maxTokens,
                temperature
        );

        return openaiWebClient
                .post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> response.choices().get(0).message().content())
                .onErrorResume(error -> {
                    // Log error and return fallback
                    return Mono.just("ERROR: Could not generate recommendations");
                });
    }

    public Mono<String> analyzeUserBehavior(String userBehaviorData) {
        String prompt = "Analyze this user behavior data and provide insights for content personalization:\n" + userBehaviorData;

        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(
                        new ChatMessage("system", "You are a user behavior analyst. Provide actionable insights for content recommendation."),
                        new ChatMessage("user", prompt)
                ),
                300,
                0.5
        );

        return openaiWebClient
                .post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> response.choices().get(0).message().content())
                .onErrorResume(error -> Mono.just("Could not analyze user behavior"));
    }

    // Request/Response DTOs
    public record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature
    ) {}

    public record ChatMessage(
            String role,
            String content
    ) {}

    public record ChatCompletionResponse(
            List<Choice> choices
    ) {}

    public record Choice(
            ChatMessage message
    ) {}
}