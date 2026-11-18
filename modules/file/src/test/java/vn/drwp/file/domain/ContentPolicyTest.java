package vn.drwp.file.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ContentPolicyTest {

    private final ContentPolicy policy = new ContentPolicy(1024 * 1024, Set.of("application/pdf", "image/png", "text/plain"));

    @Test
    void acceptsMatchingPdf() {
        assertTrue(policy.check("don.pdf", "application/pdf", 100, "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)).accepted());
    }

    @Test
    void rejectsSpoofedContent() {
        assertEquals("CONTENT_MISMATCH", policy.check("x.pdf", "application/pdf", 10, "MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1)).reason());
        assertEquals("EXTENSION_MISMATCH", policy.check("x.exe", "application/pdf", 10, "%PDF".getBytes(StandardCharsets.US_ASCII)).reason());
        assertEquals("CONTENT_TYPE_NOT_ALLOWED", policy.check("x.html", "text/html", 10, "<html>".getBytes(StandardCharsets.US_ASCII)).reason());
        assertEquals("TOO_LARGE", policy.check("x.pdf", "application/pdf", 2 * 1024 * 1024, "%PDF".getBytes(StandardCharsets.US_ASCII)).reason());
        assertEquals("EMPTY_FILE", policy.check("x.pdf", "application/pdf", 0, new byte[0]).reason());
    }

    @Test
    void sanitizesNames() {
        assertEquals("passwd", ContentPolicy.sanitizeName("../../etc/passwd"));
        assertEquals("a_b.txt", ContentPolicy.sanitizeName("C:\\temp\\a\"b.txt"));
    }
}
