package vn.drwp.platform.kernel.error;

/** Một vi phạm validate, gắn với đường dẫn trường. */
public record Violation(String field, String code, String message) {
}
