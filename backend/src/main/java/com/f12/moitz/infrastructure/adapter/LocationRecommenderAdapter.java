package com.f12.moitz.infrastructure.adapter;

import com.f12.moitz.application.dto.RecommendedLocationsResponse;
import com.f12.moitz.application.port.LocationRecommender;
import com.f12.moitz.common.error.exception.ExternalApiException;
import com.f12.moitz.common.error.exception.RetryableApiException;
import com.f12.moitz.infrastructure.client.gemini.GoogleGeminiClient;
import com.f12.moitz.infrastructure.client.perplexity.PerplexityClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationRecommenderAdapter implements LocationRecommender {

    private final GoogleGeminiClient geminiClient;
    private final PerplexityClient perplexityClient;
    private final CircuitBreaker geminiBreaker;
    private final CircuitBreaker geminiRetryableBreaker;

    @Retryable(
            retryFor = RetryableApiException.class,
            maxAttempts = 2,
            recover = "recoverRecommendedLocations"
    )
    @Override
    public RecommendedLocationsResponse recommendLocations(
            final List<String> startingPlaces,
            final List<String> candidatePlaces,
            final String requirement
    ) {
        final StopWatch stopWatch = new StopWatch("Gemini API 호출");
        stopWatch.start();
        final Supplier<RecommendedLocationsResponse> geminiCall = () -> geminiClient.generateResponse(
                startingPlaces,
                candidatePlaces,
                requirement
        );

        final Supplier<RecommendedLocationsResponse> decoratedGeminiCall = Decorators.ofSupplier(geminiCall)
                .withCircuitBreaker(geminiBreaker)
                .withCircuitBreaker(geminiRetryableBreaker)
                .withFallback(
                        List.of(ExternalApiException.class, CallNotPermittedException.class),
                        throwable -> fallback(startingPlaces, requirement)
                )
                .decorate();

        final RecommendedLocationsResponse generatedResponse = decoratedGeminiCall.get();
        stopWatch.stop();
        log.debug("Gemini API 호출 완료. 소요시간: {}s", stopWatch.getTotalTimeSeconds());

        final RecommendedLocationsResponse deduplicatedLocations = deduplicateLocation(generatedResponse);

        return excludeStartPlaces(deduplicatedLocations, startingPlaces);
    }

    private RecommendedLocationsResponse fallback(final List<String> startingPlaces, final String requirement) {
        log.debug("FallBack: Perplexity 호출을 시도합니다.");
        return perplexityClient.generateResponse(startingPlaces, requirement);
    }

    @Recover
    public RecommendedLocationsResponse recoverRecommendedLocations(
            final List<String> startingPlaces,
            final List<String> candidatePlaces,
            final String requirement
    ) {
        final RecommendedLocationsResponse generatedResponse = perplexityClient.generateResponse(
                startingPlaces,
                requirement
        );
        final RecommendedLocationsResponse deduplicatedLocations = deduplicateLocation(generatedResponse);
        return excludeStartPlaces(deduplicatedLocations, startingPlaces);
    }

    private RecommendedLocationsResponse deduplicateLocation(final RecommendedLocationsResponse response) {
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
