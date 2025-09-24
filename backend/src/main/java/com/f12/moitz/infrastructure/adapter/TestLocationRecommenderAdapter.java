package com.f12.moitz.infrastructure.adapter;

import com.f12.moitz.application.dto.RecommendedLocationsResponse;
import com.f12.moitz.application.port.LocationRecommender;
import com.f12.moitz.common.error.exception.ExternalApiException;
import com.f12.moitz.common.error.exception.RetryableApiException;
import com.f12.moitz.infrastructure.client.gemini.GoogleGeminiClient;
import com.f12.moitz.infrastructure.client.perplexity.PerplexityClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.decorators.Decorators;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestLocationRecommenderAdapter implements LocationRecommender {

    private final GoogleGeminiClient geminiClient;
    private final PerplexityClient perplexityClient;

    @Retryable(
            retryFor = RetryableApiException.class,
            maxAttempts = 2,
            recover = "recoverRecommendedLocations"
    )
    @Override
    public RecommendedLocationsResponse recommendLocations(
            final List<String> startPlaceNames,
            final String condition
    ) {
        CircuitBreakerConfig configForQuota = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(10000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .recordExceptions(ExternalApiException.class)
                .ignoreExceptions(RetryableApiException.class)
                .build();

        CircuitBreaker breakerForQuota = CircuitBreaker.of("breakerForQuota", configForQuota);

        CircuitBreakerConfig configForRetryable = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofMillis(10000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(1)
                .recordExceptions(RetryableApiException.class)
                .ignoreExceptions(ExternalApiException.class)
                .build();

        CircuitBreaker breakerForRetryable = CircuitBreaker.of("breakerForRetryable", configForRetryable);

        Supplier<RecommendedLocationsResponse> decoratedSupplier = Decorators.ofSupplier(() -> geminiClient.generateForTest("Say \"Hello, world!\""))
                .withCircuitBreaker(breakerForQuota)
                .withCircuitBreaker(breakerForRetryable)
                .withFallback(List.of(ExternalApiException.class), throwable -> fallback())
                .decorate();

        final RecommendedLocationsResponse generatedResponse = decoratedSupplier.get();

        final RecommendedLocationsResponse deduplicatedLocations = deduplicateLocation(generatedResponse);
        return excludeStartPlaces(
                deduplicatedLocations,
                startPlaceNames
        );
    }

    private RecommendedLocationsResponse fallback() {
        log.info("FallBack: Gemini 호출 응답 실패. Perplexity 호출을 시도합니다.");
        return perplexityClient.generateForTest("Say \"Hello, world!\"");
    }

    @Recover
    public RecommendedLocationsResponse recoverRecommendedLocations(
            final List<String> startPlaceNames,
            final String condition
    ) {
        log.info("Gemini 호출 응답 실패. Perplexity 호출을 시도합니다.");
        final RecommendedLocationsResponse generatedResponse = perplexityClient.generateForTest("Say \"Hello, world!\"");
        final RecommendedLocationsResponse deduplicatedLocations = deduplicateLocation(generatedResponse);
        return excludeStartPlaces(
                deduplicatedLocations,
                startPlaceNames
        );
    }

    private RecommendedLocationsResponse deduplicateLocation(
            final RecommendedLocationsResponse response
    ) {
        return new RecommendedLocationsResponse(
                response.recommendations().stream()
                        .distinct()
                        .toList()
        );
    }

    private RecommendedLocationsResponse excludeStartPlaces(
            final RecommendedLocationsResponse response,
            final List<String> startPlaceNames
    ) {
        return new RecommendedLocationsResponse(
                response.recommendations().stream()
                        .filter(recommendation -> !startPlaceNames.contains(recommendation.locationName()))
                        .toList()
        );
    }

}
