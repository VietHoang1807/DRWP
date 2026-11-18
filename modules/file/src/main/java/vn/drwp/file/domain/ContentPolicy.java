package vn.drwp.file.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Kiểm tra tối thiểu trước khi nhận tệp: kích thước, allowlist content type + đuôi, magic bytes khớp kiểu khai báo.
 * Quét mã độc (ClamAV) cắm vào sau qua {@code ContentScanner} (tài liệu 01 mục 9: malware scan/quarantine).
 */
public final class ContentPolicy {

    public record Verdict(boolean accepted, String reason) {
        static Verdict ok() {
            return new Verdict(true, "OK");
        }

        static Verdict reject(String reason) {
            return new Verdict(false, reason);
        }
    }

    private static final Map<String, Set<String>> EXTENSIONS = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/png", Set.of("png"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "text/plain", Set.of("txt", "csv"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx"),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx"));

    private final long maxBytes;
    private final Set<String> allowedTypes;

    public ContentPolicy(long maxBytes, Set<String> allowedTypes) {
        this.maxBytes = maxBytes;
        this.allowedTypes = allowedTypes;
    }

    public Verdict check(String fileName, String contentType, long size, byte[] head) {
        if (size <= 0) {
            return Verdict.reject("EMPTY_FILE");
        }
        if (size > maxBytes) {
            return Verdict.reject("TOO_LARGE");
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!allowedTypes.contains(type) || !EXTENSIONS.containsKey(type)) {
            return Verdict.reject("CONTENT_TYPE_NOT_ALLOWED");
        }
        String ext = extension(fileName);
        if (!EXTENSIONS.get(type).contains(ext)) {
            return Verdict.reject("EXTENSION_MISMATCH");
        }
        if (!magicMatches(type, head)) {
            return Verdict.reject("CONTENT_MISMATCH");
        }
        return Verdict.ok();
    }

    static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static boolean magicMatches(String type, byte[] h) {
        return switch (type) {
            case "application/pdf" -> starts(h, 0x25, 0x50, 0x44, 0x46); // %PDF
            case "image/png" -> starts(h, 0x89, 0x50, 0x4E, 0x47);
            case "image/jpeg" -> starts(h, 0xFF, 0xD8, 0xFF);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> starts(h, 0x50, 0x4B, 0x03, 0x04); // zip
            case "text/plain" -> isText(h);
            default -> false;
        };
    }

    private static boolean starts(byte[] h, int... magic) {
        if (h == null || h.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((h[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isText(byte[] h) {
        if (h == null) {
            return false;
        }
        for (byte b : h) {
            int c = b & 0xFF;
            if (c == 0 || (c < 0x09) || (c > 0x0D && c < 0x20 && c != 0x1B)) {
                return false;
            }
        }
        return true;
    }

    /** Tên hiển thị an toàn: bỏ đường dẫn, ký tự điều khiển, giới hạn độ dài. */
    public static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"<>|*?:]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
