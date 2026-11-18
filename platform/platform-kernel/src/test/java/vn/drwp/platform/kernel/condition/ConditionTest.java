package vn.drwp.platform.kernel.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConditionTest {

    @Test
    void comparesNumbersAcrossTypes() {
        Condition c = ConditionParser.parse(Map.of("field", "days", "op", "gt", "value", 5));
        assertTrue(ConditionEvaluator.evaluate(c, Map.of("days", 6)));
        assertTrue(ConditionEvaluator.evaluate(c, Map.of("days", 5.5)));
        assertFalse(ConditionEvaluator.evaluate(c, Map.of("days", 5)));
        assertFalse(ConditionEvaluator.evaluate(c, Map.of()));
    }

    @Test
    void supportsBooleanCompositionAndNestedPaths() {
        Condition c = ConditionParser.parse(Map.of("all", List.of(
                Map.of("field", "applicant.department", "op", "eq", "value", "IT"),
                Map.of("any", List.of(
                        Map.of("field", "type", "op", "in", "value", List.of("ANNUAL", "SICK")),
                        Map.of("not", Map.of("field", "urgent", "op", "eq", "value", false)))))));
        Map<String, Object> data = Map.of("applicant", Map.of("department", "IT"), "type", "UNPAID", "urgent", true);
        assertTrue(ConditionEvaluator.evaluate(c, data));
        assertFalse(ConditionEvaluator.evaluate(c, Map.of("applicant", Map.of("department", "HR"), "type", "SICK")));
    }

    @Test
    void emptyAndContains() {
        Map<String, Object> data = new HashMap<>();
        data.put("note", "  ");
        data.put("tags", List.of("a", "b"));
        assertTrue(ConditionEvaluator.evaluate(ConditionParser.parse(Map.of("field", "note", "op", "empty")), data));
        assertTrue(ConditionEvaluator.evaluate(ConditionParser.parse(Map.of("field", "tags", "op", "contains", "value", "b")), data));
        assertTrue(ConditionEvaluator.evaluate(ConditionParser.parse(Map.of("field", "missing", "op", "empty")), data));
    }

    @Test
    void isoDatesCompareLexicographically() {
        Condition c = ConditionParser.parse(Map.of("field", "from", "op", "gte", "value", "2026-01-01"));
        assertTrue(ConditionEvaluator.evaluate(c, Map.of("from", "2026-03-15")));
        assertFalse(ConditionEvaluator.evaluate(c, Map.of("from", "2025-12-31")));
    }

    @Test
    void rejectsUnknownOperatorsKeysAndExpressions() {
        assertThrows(ConditionException.class, () -> ConditionParser.parse(Map.of("field", "a", "op", "matches", "value", ".*")));
        assertThrows(ConditionException.class, () -> ConditionParser.parse(Map.of("field", "a", "op", "eq", "value", "x", "script", "1")));
        assertThrows(ConditionException.class, () -> ConditionParser.parse(Map.of("field", "T(java.lang.Runtime)", "op", "eq", "value", 1)));
        assertThrows(ConditionException.class, () -> ConditionParser.parse(Map.of("field", "a", "op", "eq", "value", Map.of("x", 1))));
        assertThrows(ConditionException.class, () -> ConditionParser.parse(Map.of("all", List.of())));
    }

    @Test
    void enforcesDepthAndSizeLimits() {
        Object deep = Map.of("field", "a", "op", "eq", "value", 1);
        for (int i = 0; i < ConditionParser.MAX_DEPTH + 1; i++) {
            deep = Map.of("not", deep);
        }
        Object finalDeep = deep;
        assertThrows(ConditionException.class, () -> ConditionParser.parse(finalDeep));
    }

    @Test
    void roundTripsThroughWriter() {
        Map<String, Object> raw = Map.of("any", List.of(Map.of("field", "a", "op", "lt", "value", 3),
                Map.of("field", "b", "op", "notEmpty")));
        Condition parsed = ConditionParser.parse(raw);
        assertEquals(parsed, ConditionParser.parse(ConditionWriter.toTree(parsed)));
    }
}
