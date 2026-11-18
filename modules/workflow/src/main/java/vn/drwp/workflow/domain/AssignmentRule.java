package vn.drwp.workflow.domain;

import java.util.Map;
import java.util.regex.Pattern;

import vn.drwp.platform.kernel.condition.ConditionParser;

/**
 * Phân công task: ROLE (mọi người có vai trò), USER (subject cố định), SUBMITTER (người nộp),
 * FIELD (subject lấy từ trường USER của hồ sơ). SUBMITTER/FIELD được giải thành USER khi tạo task.
 */
public record AssignmentRule(Type type, String value) {

    public enum Type { ROLE, USER, SUBMITTER, FIELD }

    private static final Pattern ROLE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    private static final Pattern SUBJECT = Pattern.compile("^[A-Za-z0-9._@:-]{1,100}$");

    public AssignmentRule {
        switch (type) {
            case ROLE -> require(value != null && ROLE.matcher(value).matches(), "ROLE assignment needs a role code");
            case USER -> require(value != null && SUBJECT.matcher(value).matches(), "USER assignment needs a subject");
            case FIELD -> require(ConditionParser.isValidFieldPath(value), "FIELD assignment needs a field path");
            case SUBMITTER -> value = null;
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Assignee cụ thể: (USER, subject) hoặc (ROLE, code). */
    public record Assignee(String type, String value) {
    }

    public Assignee resolve(String submitter, Map<String, Object> data) {
        return switch (type) {
            case ROLE -> new Assignee("ROLE", value);
            case USER -> new Assignee("USER", value);
            case SUBMITTER -> new Assignee("USER", submitter);
            case FIELD -> {
                Object v = data;
                for (String seg : value.split("\\.")) {
                    v = v instanceof Map<?, ?> m ? m.get(seg) : null;
                }
                if (!(v instanceof String s) || !SUBJECT.matcher(s).matches()) {
                    throw new IllegalStateException("Assignment field " + value + " does not contain a user subject");
                }
                yield new Assignee("USER", s);
            }
        };
    }
}
