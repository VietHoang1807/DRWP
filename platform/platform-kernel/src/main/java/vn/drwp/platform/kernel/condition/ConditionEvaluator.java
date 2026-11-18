package vn.drwp.platform.kernel.condition;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Đánh giá {@link Condition} trên dữ liệu hồ sơ (Map). Thuần hàm, không side effect. */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static boolean evaluate(Condition condition, Map<String, ?> data) {
        if (condition == null) {
            return true;
        }
        return switch (condition) {
            case Condition.All all -> all.conditions().stream().allMatch(c -> evaluate(c, data));
            case Condition.Any any -> any.conditions().stream().anyMatch(c -> evaluate(c, data));
            case Condition.Not not -> !evaluate(not.condition(), data);
            case Condition.Compare cmp -> compare(cmp, resolve(data, cmp.field()));
        };
    }

    static Object resolve(Map<String, ?> data, String path) {
        Object current = data;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static boolean compare(Condition.Compare cmp, Object actual) {
        Object expected = cmp.value();
        return switch (cmp.operator()) {
            case EMPTY -> isEmpty(actual);
            case NOT_EMPTY -> !isEmpty(actual);
            case EQ -> scalarEquals(actual, expected);
            case NE -> !scalarEquals(actual, expected);
            case GT -> order(actual, expected) > 0;
            case GTE -> order(actual, expected) >= 0;
            case LT -> order(actual, expected) < 0 && actual != null;
            case LTE -> actual != null && order(actual, expected) <= 0;
            case IN -> ((List<?>) expected).stream().anyMatch(e -> scalarEquals(actual, e));
            case NOT_IN -> ((List<?>) expected).stream().noneMatch(e -> scalarEquals(actual, e));
            case CONTAINS -> contains(actual, expected);
        };
    }

    private static boolean isEmpty(Object v) {
        return v == null
                || (v instanceof String s && s.isBlank())
                || (v instanceof Collection<?> c && c.isEmpty())
                || (v instanceof Map<?, ?> m && m.isEmpty());
    }

    private static boolean scalarEquals(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return toDecimal(na).compareTo(toDecimal(nb)) == 0;
        }
        if (a instanceof Number na && b instanceof String sb) {
            BigDecimal parsed = tryDecimal(sb);
            return parsed != null && toDecimal(na).compareTo(parsed) == 0;
        }
        if (a instanceof String sa && b instanceof Number nb) {
            BigDecimal parsed = tryDecimal(sa);
            return parsed != null && parsed.compareTo(toDecimal(nb)) == 0;
        }
        return Objects.equals(a, b);
    }

    /** Thứ tự: số so theo giá trị, chuỗi so theo từ điển (ngày ISO-8601 so đúng). null nhỏ nhất. */
    private static int order(Object actual, Object expected) {
        if (actual == null) {
            return -1;
        }
        BigDecimal a = actual instanceof Number n ? toDecimal(n) : actual instanceof String s ? tryDecimal(s) : null;
        BigDecimal e = expected instanceof Number n ? toDecimal(n) : expected instanceof String s ? tryDecimal(s) : null;
        if (a != null && e != null) {
            return a.compareTo(e);
        }
        if (actual instanceof String sa && expected instanceof String se) {
            return sa.compareTo(se);
        }
        return -1; // kiểu không so sánh được: coi như không thỏa
    }

    private static boolean contains(Object actual, Object expected) {
        if (actual instanceof String s && expected instanceof String e) {
            return s.contains(e);
        }
        if (actual instanceof Collection<?> c) {
            return c.stream().anyMatch(item -> scalarEquals(item, expected));
        }
        return false;
    }

    private static BigDecimal toDecimal(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd;
        }
        if (n instanceof Double || n instanceof Float) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(n.toString());
    }

    private static BigDecimal tryDecimal(String s) {
        if (s == null || s.isBlank() || s.length() > 40) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
