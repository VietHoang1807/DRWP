package vn.drwp.platform.kernel.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parse cây Map/List (đã đọc từ JSON) thành {@link Condition}. Từ chối mọi khóa không có trong allowlist.
 */
public final class ConditionParser {

    public static final int MAX_DEPTH = 6;
    public static final int MAX_NODES = 64;
    public static final int MAX_LIST_SIZE = 100;
    public static final int MAX_STRING_LENGTH = 500;
    private static final Pattern FIELD_PATH = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,49}(\\.[a-zA-Z][a-zA-Z0-9_]{0,49}){0,2}$");

    private int nodes;

    private ConditionParser() {
    }

    /** @return null nếu {@code raw} null. */
    public static Condition parse(Object raw) {
        if (raw == null) {
            return null;
        }
        return new ConditionParser().node(raw, 1);
    }

    public static boolean isValidFieldPath(String path) {
        return path != null && FIELD_PATH.matcher(path).matches();
    }

    private Condition node(Object raw, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ConditionException("Condition nested too deep (max " + MAX_DEPTH + ")");
        }
        if (++nodes > MAX_NODES) {
            throw new ConditionException("Condition too large (max " + MAX_NODES + " nodes)");
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ConditionException("Condition node must be an object");
        }
        if (map.containsKey("all")) {
            onlyKeys(map, "all");
            return new Condition.All(list(map.get("all"), depth));
        }
        if (map.containsKey("any")) {
            onlyKeys(map, "any");
            return new Condition.Any(list(map.get("any"), depth));
        }
        if (map.containsKey("not")) {
            onlyKeys(map, "not");
            return new Condition.Not(node(map.get("not"), depth + 1));
        }
        if (map.containsKey("field")) {
            for (Object key : map.keySet()) {
                if (!"field".equals(key) && !"op".equals(key) && !"value".equals(key)) {
                    throw new ConditionException("Unsupported key in comparison: " + key);
                }
            }
            Object field = map.get("field");
            if (!(field instanceof String f) || !isValidFieldPath(f)) {
                throw new ConditionException("Invalid field path: " + field);
            }
            Object opRaw = map.get("op");
            if (!(opRaw instanceof String opToken)) {
                throw new ConditionException("Missing operator for field " + f);
            }
            Condition.Operator op = Condition.Operator.fromToken(opToken);
            Object value = map.get("value");
            checkValue(op, value);
            return new Condition.Compare(f, op, value);
        }
        throw new ConditionException("Unknown condition node, expected all/any/not/field");
    }

    private List<Condition> list(Object raw, int depth) {
        if (!(raw instanceof List<?> items) || items.isEmpty()) {
            throw new ConditionException("all/any requires a non-empty array");
        }
        List<Condition> result = new ArrayList<>(items.size());
        for (Object item : items) {
            result.add(node(item, depth + 1));
        }
        return result;
    }

    private static void onlyKeys(Map<?, ?> map, String key) {
        if (map.size() != 1) {
            throw new ConditionException("Node '" + key + "' must not have sibling keys");
        }
    }

    private static void checkValue(Condition.Operator op, Object value) {
        switch (op) {
            case EMPTY, NOT_EMPTY -> {
                if (value != null) {
                    throw new ConditionException(op.token() + " does not take a value");
                }
            }
            case IN, NOT_IN -> {
                if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > MAX_LIST_SIZE) {
                    throw new ConditionException(op.token() + " requires an array of 1.." + MAX_LIST_SIZE + " scalars");
                }
                list.forEach(ConditionParser::checkScalar);
            }
            default -> {
                if (value == null) {
                    throw new ConditionException(op.token() + " requires a value");
                }
                checkScalar(value);
            }
        }
    }

    private static void checkScalar(Object v) {
        if (v instanceof String s) {
            if (s.length() > MAX_STRING_LENGTH) {
                throw new ConditionException("String constant too long");
            }
        } else if (!(v instanceof Number) && !(v instanceof Boolean)) {
            throw new ConditionException("Only string/number/boolean constants are allowed");
        }
    }
}
