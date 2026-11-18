package vn.drwp.platform.kernel.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Mã tài nguyên cho phân quyền. Dạng: {@code *}, {@code <type>}, {@code <type>:*}, {@code <type>:<CODE>}.
 * Ví dụ: {@code record:LEAVE_REQUEST}, {@code wf:LEAVE_APPROVAL}, {@code metadata}, {@code identity}.
 */
public final class ResourceCode {

    public static final String WILDCARD = "*";
    private static final Pattern FORMAT = Pattern.compile("^(\\*|[a-z][a-z0-9-]{0,30}(:(\\*|[A-Z][A-Z0-9_]{0,63}))?)$");

    private ResourceCode() {
    }

    public static boolean isValid(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }

    public static String requireValid(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("Invalid resource code: " + code);
        }
        return code;
    }

    public static String of(String type, String code) {
        return requireValid(type + ":" + code);
    }

    /**
     * Các mã grant có thể khớp với tài nguyên cụ thể, từ cụ thể đến tổng quát.
     * {@code record:LEAVE} -> [record:LEAVE, record:*, record, *].
     */
    public static List<String> matchingGrants(String resource) {
        requireValid(resource);
        List<String> result = new ArrayList<>(4);
        result.add(resource);
        int idx = resource.indexOf(':');
        if (idx > 0) {
            String type = resource.substring(0, idx);
            result.add(type + ":*");
            result.add(type);
        }
        if (!WILDCARD.equals(resource)) {
            result.add(WILDCARD);
        }
        return result;
    }
}
