package com.f12.moitz.infrastructure.client.perplexity.dto;

import java.util.List;

public record TestPerplexityRequest(
        String model,
        List<PerplexityRequest.Message> messages
) {

    public record Message(
            String role,
            String content
    ) {

    }

}
