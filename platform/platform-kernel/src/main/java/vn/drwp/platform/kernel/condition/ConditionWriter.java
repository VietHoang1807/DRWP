package vn.drwp.platform.kernel.condition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Chuyển {@link Condition} ngược về cây Map để lưu/serialize. */
public final class ConditionWriter {

    private ConditionWriter() {
    }

    public static Object toTree(Condition condition) {
        if (condition == null) {
            return null;
        }
        return switch (condition) {
            case Condition.All all -> Map.of("all", all.conditions().stream().map(ConditionWriter::toTree).toList());
            case Condition.Any any -> Map.of("any", any.conditions().stream().map(ConditionWriter::toTree).toList());
            case Condition.Not not -> Map.of("not", toTree(not.condition()));
            case Condition.Compare cmp -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("field", cmp.field());
                m.put("op", cmp.operator().token());
                if (cmp.value() != null) {
                    m.put("value", cmp.value() instanceof List<?> l ? List.copyOf(l) : cmp.value());
                }
                yield m;
            }
        };
    }
}
