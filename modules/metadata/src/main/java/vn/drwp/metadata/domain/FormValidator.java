package vn.drwp.metadata.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import vn.drwp.platform.kernel.condition.ConditionEvaluator;
import vn.drwp.platform.kernel.error.Violation;

/** Kiểm tra dữ liệu hồ sơ theo lược đồ. Hàm thuần, kết quả xác định (deterministic). */
public final class FormValidator {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$");
    private static final Pattern SUBJECT = Pattern.compile("^[A-Za-z0-9._@:-]{1,100}$");
    private static final int MAX_FILES = 20;

    private FormValidator() {
    }

    public static List<Violation> validate(FormSchema schema, Map<String, Object> data, boolean full) {
        List<Violation> out = new ArrayList<>();
        Map<String, Object> values = data == null ? Map.of() : data;
        for (String key : values.keySet()) {
            if (schema.field(key).isEmpty()) {
                out.add(new Violation(key, "UNKNOWN_FIELD", "Field is not defined in the form"));
            }
        }
        for (FieldDefinition f : schema.fields()) {
            Object v = values.get(f.key());
            if (isMissing(v)) {
                if (full && (f.required() || (f.requiredIf() != null && ConditionEvaluator.evaluate(f.requiredIf(), values)))) {
                    out.add(new Violation(f.key(), "REQUIRED", f.label() + " is required"));
                }
                continue;
            }
            checkValue(f, v, out);
        }
        return out;
    }

    private static boolean isMissing(Object v) {
        return v == null || (v instanceof String s && s.isBlank()) || (v instanceof Collection<?> c && c.isEmpty());
    }

    private static void checkValue(FieldDefinition f, Object v, List<Violation> out) {
        String k = f.key();
        switch (f.type()) {
            case TEXT, TEXTAREA, EMAIL -> {
                if (!(v instanceof String s)) {
                    out.add(type(k, "string"));
                    return;
                }
                if (s.length() > f.effectiveMaxLength()) {
                    out.add(new Violation(k, "MAX_LENGTH", "At most " + f.effectiveMaxLength() + " characters"));
                    return;
                }
                if (f.minLength() != null && s.length() < f.minLength()) {
                    out.add(new Violation(k, "MIN_LENGTH", "At least " + f.minLength() + " characters"));
                }
                if (f.type() == FieldType.EMAIL && !EMAIL.matcher(s).matches()) {
                    out.add(new Violation(k, "EMAIL", "Invalid email address"));
                }
                if (f.pattern() != null) {
                    try {
                        if (!SafeRegex.matches(SafeRegex.compile(f.pattern()), s)) {
                            out.add(new Violation(k, "PATTERN", "Value does not match the required format"));
                        }
                    } catch (IllegalStateException timeout) {
                        out.add(new Violation(k, "PATTERN_TIMEOUT", "Value could not be validated"));
                    }
                }
            }
            case NUMBER, INTEGER -> {
                if (!(v instanceof Number n)) {
                    out.add(type(k, "number"));
                    return;
                }
                BigDecimal d = new BigDecimal(n.toString());
                if (f.type() == FieldType.INTEGER && d.stripTrailingZeros().scale() > 0) {
                    out.add(type(k, "integer"));
                    return;
                }
                if (f.min() != null && d.compareTo(f.min()) < 0) {
                    out.add(new Violation(k, "MIN", "Must be >= " + f.min().toPlainString()));
                }
                if (f.max() != null && d.compareTo(f.max()) > 0) {
                    out.add(new Violation(k, "MAX", "Must be <= " + f.max().toPlainString()));
                }
            }
            case BOOLEAN -> {
                if (!(v instanceof Boolean)) {
                    out.add(type(k, "boolean"));
                }
            }
            case DATE -> {
                if (!(v instanceof String s) || !parses(() -> LocalDate.parse(s))) {
                    out.add(type(k, "date (yyyy-MM-dd)"));
                }
            }
            case DATETIME -> {
                if (!(v instanceof String s) || !parses(() -> OffsetDateTime.parse(s))) {
                    out.add(type(k, "ISO-8601 date-time with offset"));
                }
            }
            case SELECT -> {
                if (!(v instanceof String s) || !f.options().contains(s)) {
                    out.add(new Violation(k, "OPTION", "Value must be one of the allowed options"));
                }
            }
            case MULTISELECT -> {
                if (!(v instanceof List<?> list) || list.stream().anyMatch(x -> !(x instanceof String s) || !f.options().contains(s))
                        || new HashSet<>(list).size() != list.size()) {
                    out.add(new Violation(k, "OPTION", "Values must be distinct allowed options"));
                }
            }
            case FILE -> {
                if (!(v instanceof List<?> list) || list.size() > MAX_FILES
                        || list.stream().anyMatch(x -> !(x instanceof String s) || !parses(() -> UUID.fromString(s)))) {
                    out.add(type(k, "array of file ids (max " + MAX_FILES + ")"));
                }
            }
            case USER -> {
                if (!(v instanceof String s) || !SUBJECT.matcher(s).matches()) {
                    out.add(type(k, "user subject"));
                }
            }
        }
    }

    private static Violation type(String key, String expected) {
        return new Violation(key, "TYPE", "Expected " + expected);
    }

    private static boolean parses(Runnable r) {
        try {
            r.run();
            return true;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return false;
        }
    }
}
