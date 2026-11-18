package vn.drwp.metadata.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import vn.drwp.platform.kernel.condition.Condition;

/** Định nghĩa một trường. Ràng buộc chỉ gồm các khóa allowlist; không có biểu thức tự do. */
public record FieldDefinition(
        String key,
        String label,
        FieldType type,
        boolean required,
        Condition requiredIf,
        List<String> options,
        Integer minLength,
        Integer maxLength,
        BigDecimal min,
        BigDecimal max,
        String pattern,
        boolean filterable,
        boolean sensitive,
        String description) {

    public static final Pattern KEY = Pattern.compile("^[a-z][a-zA-Z0-9_]{0,49}$");
    public static final int MAX_OPTIONS = 200;
    public static final int MAX_PATTERN_LENGTH = 200;

    public FieldDefinition {
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** Độ dài tối đa áp dụng (mặc định theo kiểu). */
    public int effectiveMaxLength() {
        if (maxLength != null) {
            return maxLength;
        }
        return type == FieldType.TEXTAREA ? 10_000 : 500;
    }
}
