package vn.drwp.platform.json;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Tiện ích JSON (Jackson 3) cho tầng infrastructure. Domain chỉ làm việc với Map/List. */
@Component
public class Json {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST = new TypeReference<>() {
    };

    private final JsonMapper mapper;

    public Json(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public Map<String, Object> readMap(String json) {
        return json == null ? null : mapper.readValue(json, MAP);
    }

    public List<Object> readList(String json) {
        return json == null ? null : mapper.readValue(json, LIST);
    }

    public <T> T read(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    public JsonMapper mapper() {
        return mapper;
    }
}
