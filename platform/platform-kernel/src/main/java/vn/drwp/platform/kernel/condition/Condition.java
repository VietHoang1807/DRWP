package vn.drwp.platform.kernel.condition;

import java.util.List;

/**
 * DSL điều kiện có allowlist (tài liệu 02 mục 1 điểm 5): không thực thi Java/SQL/SpEL.
 * Chỉ có các toán tử khai báo ở {@link Operator}, giới hạn độ sâu và số node khi parse.
 */
public sealed interface Condition permits Condition.All, Condition.Any, Condition.Not, Condition.Compare {

    record All(List<Condition> conditions) implements Condition {
        public All {
            conditions = List.copyOf(conditions);
        }
    }

    record Any(List<Condition> conditions) implements Condition {
        public Any {
            conditions = List.copyOf(conditions);
        }
    }

    record Not(Condition condition) implements Condition {
    }

    /** So sánh một trường dữ liệu với hằng số. {@code value} có thể null với EMPTY/NOT_EMPTY. */
    record Compare(String field, Operator operator, Object value) implements Condition {
    }

    enum Operator {
        EQ("eq"), NE("ne"), GT("gt"), GTE("gte"), LT("lt"), LTE("lte"),
        IN("in"), NOT_IN("notIn"), CONTAINS("contains"), EMPTY("empty"), NOT_EMPTY("notEmpty");

        private final String token;

        Operator(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public static Operator fromToken(String token) {
            for (Operator op : values()) {
                if (op.token.equals(token)) {
                    return op;
                }
            }
            throw new ConditionException("Unsupported operator: " + token);
        }
    }
}
