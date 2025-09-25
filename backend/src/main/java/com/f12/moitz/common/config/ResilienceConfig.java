package com.f12.moitz.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnIgnoredErrorEvent;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

    private final CircuitBreakerRegistry registry;

    @Bean
    public CircuitBreaker geminiBreaker() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        TaggedCircuitBreakerMetrics
                .ofCircuitBreakerRegistry(registry)
                .bindTo(meterRegistry);

        final CircuitBreaker circuitBreaker = registry.circuitBreaker("gemini");
        circuitBreaker.getEventPublisher();

        return circuitBreaker;
    }

    @Bean
    public CircuitBreaker geminiRetryableBreaker() {
        final CircuitBreaker circuitBreaker = registry.circuitBreaker("geminiRetryable");
        circuitBreaker.getEventPublisher()
                .onIgnoredError(CircuitBreakerOnIgnoredErrorEvent::getEventType);
        return circuitBreaker;
    }

}
