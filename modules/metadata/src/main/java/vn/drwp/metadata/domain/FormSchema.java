package vn.drwp.metadata.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import vn.drwp.platform.kernel.condition.Condition;
import vn.drwp.platform.kernel.condition.ConditionException;
import vn.drwp.platform.kernel.condition.ConditionParser;
import vn.drwp.platform.kernel.condition.ConditionWriter;
import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.kernel.error.Violation;

/**
 * Lược đồ biểu mẫu. Parse từ cây Map (JSON) và kiểm tra tính hợp lệ của chính định nghĩa
 * (khóa duy nhất, kiểu hợp lệ, regex an toàn, điều kiện allowlist tham chiếu trường có thật).
 */
public record FormSchema(List<FieldDefinition> fields) {

    public static final int MAX_FIELDS = 200;
    private static final Set<String> FIELD_KEYS = Set.of("key", "label", "type", "required", "requiredIf", "options",
            "minLength", "maxLength", "min", "max", "pattern", "filterable", "sensitive", "description");

    public FormSchema {
        fields = List.copyOf(fields);
    }

    public Optional<FieldDefinition> field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    @SuppressWarnings("unchecked")
    public static FormSchema parse(Map<String, Object> raw) {
        List<Violation> violations = new ArrayList<>();
        if (raw == null || !(raw.get("fields") instanceof List<?> list)) {
            throw ValidationFailedException.single("fields", "REQUIRED", "schema.fields must be an array");
        }
        for (Object k : raw.keySet()) {
            if (!"fields".equals(k)) {
                violations.add(new Violation(String.valueOf(k), "UNKNOWN_KEY", "Unsupported schema key"));
            }
        }
        if (list.isEmpty() || list.size() > MAX_FIELDS) {
            violations.add(new Violation("fields", "SIZE", "A form must have 1.." + MAX_FIELDS + " fields"));
        }
        List<FieldDefinition> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String path = "fields[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> f)) {
                violations.add(new Violation(path, "TYPE", "Field must be an object"));
                continue;
            }
            try {
                FieldDefinition def = parseField((Map<String, Object>) f, path, violations);
                if (def != null) {
                    if (!seen.add(def.key())) {
                        violations.add(new Violation(path + ".key", "DUPLICATE", "Duplicate field key " + def.key()));
                    }
                    fields.add(def);
                }
            } catch (RuntimeException e) {
                violations.add(new Violation(path, "INVALID", e.getMessage()));
            }
        }
        // requiredIf chỉ được tham chiếu trường tồn tại
        for (FieldDefinition f : fields) {
            if (f.requiredIf() != null) {
                for (String ref : referencedFields(f.requiredIf())) {
                    if (!seen.contains(ref.split("\\.")[0])) {
                        violations.add(new Violation(f.key() + ".requiredIf", "UNKNOWN_FIELD", "References unknown field " + ref));
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }
        return new FormSchema(fields);
    }

    private static FieldDefinition parseField(Map<String, Object> f, String path, List<Violation> violations) {
        for (String k : f.keySet()) {
            if (!FIELD_KEYS.contains(k)) {
                violations.add(new Violation(path + "." + k, "UNKNOWN_KEY", "Unsupported field key"));
            }
        }
        Object keyRaw = f.get("key");
        if (!(keyRaw instanceof String key) || !FieldDefinition.KEY.matcher(key).matches()) {
            violations.add(new Violation(path + ".key", "FORMAT", "key must match " + FieldDefinition.KEY.pattern()));
            return null;
        }
        FieldType type;
        try {
            type = FieldType.valueOf(String.valueOf(f.get("type")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            violations.add(new Violation(path + ".type", "UNKNOWN_TYPE", "Unknown field type " + f.get("type")));
            return null;
        }
        Condition requiredIf = null;
        if (f.get("requiredIf") != null) {
            try {
                requiredIf = ConditionParser.parse(f.get("requiredIf"));
            } catch (ConditionException e) {
                violations.add(new Violation(path + ".requiredIf", "INVALID_CONDITION", e.getMessage()));
            }
        }
        List<String> options = new ArrayList<>();
        if (f.get("options") instanceof List<?> opts) {
            for (Object o : opts) {
                options.add(String.valueOf(o));
            }
        }
        if ((type == FieldType.SELECT || type == FieldType.MULTISELECT)) {
            if (options.isEmpty() || options.size() > FieldDefinition.MAX_OPTIONS || new HashSet<>(options).size() != options.size()) {
                violations.add(new Violation(path + ".options", "INVALID", "SELECT needs 1.." + FieldDefinition.MAX_OPTIONS + " unique options"));
            }
        } else if (!options.isEmpty()) {
            violations.add(new Violation(path + ".options", "NOT_APPLICABLE", "options only apply to SELECT/MULTISELECT"));
        }
        String pattern = f.get("pattern") instanceof String p ? p : null;
        if (pattern != null) {
            if (type != FieldType.TEXT && type != FieldType.TEXTAREA) {
                violations.add(new Violation(path + ".pattern", "NOT_APPLICABLE", "pattern only applies to TEXT/TEXTAREA"));
            } else {
                try {
                    SafeRegex.compile(pattern);
                } catch (IllegalArgumentException e) {
                    violations.add(new Violation(path + ".pattern", "INVALID_PATTERN", e.getMessage()));
                }
            }
        }
        Integer minLength = intOrNull(f.get("minLength"));
        Integer maxLength = intOrNull(f.get("maxLength"));
        if (maxLength != null && (maxLength < 1 || maxLength > 100_000)) {
            violations.add(new Violation(path + ".maxLength", "RANGE", "maxLength must be 1..100000"));
        }
        if (minLength != null && maxLength != null && minLength > maxLength) {
            violations.add(new Violation(path + ".minLength", "RANGE", "minLength > maxLength"));
        }
        BigDecimal min = decimalOrNull(f.get("min"));
        BigDecimal max = decimalOrNull(f.get("max"));
        if (min != null && max != null && min.compareTo(max) > 0) {
            violations.add(new Violation(path + ".min", "RANGE", "min > max"));
        }
        String label = f.get("label") instanceof String l ? l : key;
        return new FieldDefinition(key, label, type, Boolean.TRUE.equals(f.get("required")), requiredIf, options,
                minLength, maxLength, min, max, pattern, Boolean.TRUE.equals(f.get("filterable")),
                Boolean.TRUE.equals(f.get("sensitive")), f.get("description") instanceof String d ? d : null);
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static BigDecimal decimalOrNull(Object o) {
        return o instanceof Number n ? new BigDecimal(n.toString()) : null;
    }

    static List<String> referencedFields(Condition c) {
        List<String> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Condition c, List<String> out) {
        switch (c) {
            case Condition.All all -> all.conditions().forEach(x -> collect(x, out));
            case Condition.Any any -> any.conditions().forEach(x -> collect(x, out));
            case Condition.Not not -> collect(not.condition(), out);
            case Condition.Compare cmp -> out.add(cmp.field());
        }
    }

    /** Dạng cây chuẩn hóa để lưu JSON (thứ tự khóa ổn định -> checksum ổn định). */
    public Map<String, Object> toTree() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FieldDefinition f : fields) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", f.key());
            m.put("label", f.label());
            m.put("type", f.type().name());
            if (f.required()) {
                m.put("required", true);
            }
            if (f.requiredIf() != null) {
                m.put("requiredIf", ConditionWriter.toTree(f.requiredIf()));
            }
            if (!f.options().isEmpty()) {
                m.put("options", f.options());
            }
            putIfNotNull(m, "minLength", f.minLength());
            putIfNotNull(m, "maxLength", f.maxLength());
            putIfNotNull(m, "min", f.min());
            putIfNotNull(m, "max", f.max());
            putIfNotNull(m, "pattern", f.pattern());
            if (f.filterable()) {
                m.put("filterable", true);
            }
            if (f.sensitive()) {
                m.put("sensitive", true);
            }
            putIfNotNull(m, "description", f.description());
            list.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("fields", list);
        return root;
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) {
            m.put(k, v);
        }
    }
}
