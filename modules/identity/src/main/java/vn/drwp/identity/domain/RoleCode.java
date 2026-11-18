package vn.drwp.identity.domain;

import java.util.regex.Pattern;

public final class RoleCode {

    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    private RoleCode() {
    }

    public static String requireValid(String code) {
        if (code == null || !FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("Role code must match " + FORMAT.pattern());
        }
        return code;
    }
}
