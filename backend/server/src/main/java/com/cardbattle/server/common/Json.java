package com.cardbattle.server.common;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jackson 사용을 한 곳에 모은 도우미.
 * Spring Boot 4는 Jackson 3(tools.jackson 패키지)을 쓰고, 예외가 unchecked(JacksonException)다.
 */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String text, Class<T> type) {
        return mapper.readValue(text, type);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readMap(String text) {
        return mapper.readValue(text, Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> readListOfMaps(String text) {
        List<Object> raw = mapper.readValue(text, List.class);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("JSON 배열 안에는 객체만 올 수 있습니다");
            }
            result.add((Map<String, Object>) o);
        }
        return result;
    }
}
