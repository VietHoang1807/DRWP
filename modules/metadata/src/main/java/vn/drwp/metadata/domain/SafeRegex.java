package vn.drwp.metadata.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regex do người cấu hình nhập: giới hạn độ dài pattern, cấm backreference/lookaround và giới hạn thời gian khớp
 * (chống ReDoS) bằng CharSequence kiểm tra deadline.
 */
public final class SafeRegex {

    private static final long TIMEOUT_NANOS = 50_000_000L; // 50ms
    private static final Pattern FORBIDDEN = Pattern.compile("\\\\[1-9]|\\(\\?<?[=!]|\\(\\?<[a-zA-Z]");

    private SafeRegex() {
    }

    public static Pattern compile(String pattern) {
        if (pattern.length() > FieldDefinition.MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("pattern too long");
        }
        if (FORBIDDEN.matcher(pattern).find()) {
            throw new IllegalArgumentException("backreferences and lookaround are not allowed in pattern");
        }
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid pattern: " + e.getDescription());
        }
    }

    /** @return true nếu khớp toàn bộ; ném {@link IllegalStateException} khi quá thời gian. */
    public static boolean matches(Pattern pattern, String input) {
        Matcher m = pattern.matcher(new DeadlineCharSequence(input, System.nanoTime() + TIMEOUT_NANOS));
        return m.matches();
    }

    private record DeadlineCharSequence(CharSequence inner, long deadline) implements CharSequence {
        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("pattern evaluation timed out");
            }
            return inner.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(inner.subSequence(start, end), deadline);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }
}
