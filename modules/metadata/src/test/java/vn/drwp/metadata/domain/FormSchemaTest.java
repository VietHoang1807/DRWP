package vn.drwp.metadata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.kernel.error.Violation;

class FormSchemaTest {

    static Map<String, Object> leaveSchema() {
        return Map.of("fields", List.of(
                Map.of("key", "leaveType", "label", "Loại nghỉ", "type", "SELECT", "options", List.of("ANNUAL", "SICK", "UNPAID"),
                        "required", true, "filterable", true),
                Map.of("key", "fromDate", "label", "Từ ngày", "type", "DATE", "required", true),
                Map.of("key", "days", "label", "Số ngày", "type", "NUMBER", "min", 0.5, "max", 30, "required", true),
                Map.of("key", "reason", "label", "Lý do", "type", "TEXTAREA", "maxLength", 1000,
                        "requiredIf", Map.of("field", "days", "op", "gt", "value", 3)),
                Map.of("key", "employeeCode", "label", "Mã NV", "type", "TEXT", "pattern", "^NV[0-9]{4}$", "sensitive", true),
                Map.of("key", "attachments", "label", "Đính kèm", "type", "FILE")));
    }

    @Test
    void parsesAndNormalises() {
        FormSchema schema = FormSchema.parse(leaveSchema());
        assertEquals(6, schema.fields().size());
        assertTrue(schema.field("leaveType").orElseThrow().filterable());
        assertEquals(schema, FormSchema.parse(schema.toTree()));
    }

    @Test
    void rejectsInvalidDefinitions() {
        ValidationFailedException e = assertThrows(ValidationFailedException.class, () -> FormSchema.parse(Map.of("fields", List.of(
                Map.of("key", "a", "type", "TEXT"),
                Map.of("key", "a", "type", "TEXT"),
                Map.of("key", "Bad Key", "type", "TEXT"),
                Map.of("key", "s", "type", "SELECT"),
                Map.of("key", "x", "type", "SCRIPT"),
                Map.of("key", "r", "type", "TEXT", "pattern", "(a+)+\\1"),
                Map.of("key", "c", "type", "TEXT", "requiredIf", Map.of("field", "ghost", "op", "empty")),
                Map.of("key", "j", "type", "TEXT", "onChange", "alert(1)")))));
        List<String> codes = e.violations().stream().map(Violation::code).toList();
        assertTrue(codes.contains("DUPLICATE"));
        assertTrue(codes.contains("FORMAT"));
        assertTrue(codes.contains("INVALID"));
        assertTrue(codes.contains("UNKNOWN_TYPE"));
        assertTrue(codes.contains("INVALID_PATTERN"));
        assertTrue(codes.contains("UNKNOWN_FIELD"));
        assertTrue(codes.contains("UNKNOWN_KEY"));
    }

    @Test
    void validatesDataDeterministically() {
        FormSchema schema = FormSchema.parse(leaveSchema());
        Map<String, Object> ok = new HashMap<>(Map.of("leaveType", "ANNUAL", "fromDate", "2026-12-01", "days", 2,
                "employeeCode", "NV0001", "attachments", List.of("0195f3c2-8a7b-7cde-9f01-23456789abcd")));
        assertTrue(FormValidator.validate(schema, ok, true).isEmpty());

        Map<String, Object> bad = new HashMap<>(ok);
        bad.put("days", 5);            // > 3 => reason bắt buộc
        bad.put("leaveType", "HOLIDAY");
        bad.put("fromDate", "01/12/2026");
        bad.put("employeeCode", "X1");
        bad.put("hacker", "x");
        List<String> codes = FormValidator.validate(schema, bad, true).stream().map(v -> v.field() + ":" + v.code()).toList();
        assertTrue(codes.contains("reason:REQUIRED"));
        assertTrue(codes.contains("leaveType:OPTION"));
        assertTrue(codes.contains("fromDate:TYPE"));
        assertTrue(codes.contains("employeeCode:PATTERN"));
        assertTrue(codes.contains("hacker:UNKNOWN_FIELD"));
        assertEquals(codes, FormValidator.validate(schema, bad, true).stream().map(v -> v.field() + ":" + v.code()).toList());
    }

    @Test
    void partialValidationSkipsRequired() {
        FormSchema schema = FormSchema.parse(leaveSchema());
        assertTrue(FormValidator.validate(schema, Map.of("days", 1), false).isEmpty());
        assertEquals(3, FormValidator.validate(schema, Map.of(), true).size());
    }

    @Test
    void numericBounds() {
        FormSchema schema = FormSchema.parse(leaveSchema());
        List<String> codes = FormValidator.validate(schema, Map.of("days", 31), false).stream().map(Violation::code).toList();
        assertEquals(List.of("MAX"), codes);
        assertEquals(List.of("TYPE"), FormValidator.validate(schema, Map.of("days", "2"), false).stream().map(Violation::code).toList());
    }

    @Test
    void regexTimesOutInsteadOfHanging() {
        // backtracking theo hàm mũ: không có giới hạn thời gian sẽ treo thread
        java.util.regex.Pattern p = SafeRegex.compile("^(.*a){20}$");
        String evil = "a".repeat(30) + "b";
        assertThrows(IllegalStateException.class, () -> SafeRegex.matches(p, evil));
    }
}
