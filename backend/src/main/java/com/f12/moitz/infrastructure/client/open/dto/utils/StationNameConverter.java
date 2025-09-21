package com.f12.moitz.infrastructure.client.open.dto.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.Map;

public class StationNameConverter extends JsonDeserializer<String> {

    private static final Map<String, String> NAME_EXCEPTIONS = Map.of(
            "이수", "총신대입구역",
            "서울역", "서울역",
            "경의선 지선", "경의선"
    );

    @Override
    public String deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
        final String value = p.getText();
        return NAME_EXCEPTIONS.getOrDefault(value, value + "역");
    }

}
