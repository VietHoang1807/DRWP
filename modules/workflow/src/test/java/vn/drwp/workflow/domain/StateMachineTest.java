package vn.drwp.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.kernel.error.Violation;
import vn.drwp.platform.kernel.security.Action;

class StateMachineTest {

    /** Trưởng phòng duyệt -> (nếu > 5 ngày) Giám đốc duyệt -> Duyệt; có từ chối/trả lại; timeout tự chuyển lên giám đốc. */
    static Map<String, Object> leaveApproval() {
        return Map.of(
                "initialState", "MANAGER_REVIEW",
                "states", List.of(
                        Map.of("code", "MANAGER_REVIEW", "name", "Trưởng phòng duyệt", "type", "TASK",
                                "assignment", Map.of("type", "ROLE", "value", "MANAGER"), "timeout", "P3D", "onTimeout", "escalate"),
                        Map.of("code", "CHECK_DAYS", "type", "DECISION"),
                        Map.of("code", "DIRECTOR_REVIEW", "name", "Giám đốc duyệt", "type", "TASK",
                                "assignment", Map.of("type", "ROLE", "value", "DIRECTOR")),
                        Map.of("code", "APPROVED", "type", "END", "outcome", "APPROVED"),
                        Map.of("code", "REJECTED", "type", "END", "outcome", "REJECTED"),
                        Map.of("code", "RETURNED", "type", "END", "outcome", "RETURNED")),
                "transitions", List.of(
                        Map.of("code", "approve", "from", "MANAGER_REVIEW", "to", "CHECK_DAYS", "permission", "APPROVE"),
                        Map.of("code", "reject", "from", "MANAGER_REVIEW", "to", "REJECTED", "permission", "REJECT", "commentRequired", true),
                        Map.of("code", "return", "from", "MANAGER_REVIEW", "to", "RETURNED", "permission", "REJECT"),
                        Map.of("code", "escalate", "from", "MANAGER_REVIEW", "to", "DIRECTOR_REVIEW", "permission", "APPROVE"),
                        Map.of("code", "long", "from", "CHECK_DAYS", "to", "DIRECTOR_REVIEW",
                                "guard", Map.of("field", "days", "op", "gt", "value", 5)),
                        Map.of("code", "short", "from", "CHECK_DAYS", "to", "APPROVED"),
                        Map.of("code", "approve", "from", "DIRECTOR_REVIEW", "to", "APPROVED", "permission", "APPROVE"),
                        Map.of("code", "reject", "from", "DIRECTOR_REVIEW", "to", "REJECTED", "permission", "REJECT")));
    }

    @Test
    void parsesSpec() {
        WorkflowSpec spec = WorkflowSpec.parse(leaveApproval());
        assertEquals("MANAGER_REVIEW", spec.initialState());
        assertEquals(Action.REJECT, StateMachine.transition(spec, "MANAGER_REVIEW", "reject").permission());
        assertEquals(spec, WorkflowSpec.parse(spec.toTree()));
    }

    @Test
    void shortLeaveIsApprovedByManagerOnly() {
        WorkflowSpec spec = WorkflowSpec.parse(leaveApproval());
        StateMachine.Advance start = StateMachine.start(spec, Map.of("days", 2));
        assertFalse(start.ended());
        assertEquals("MANAGER_REVIEW", start.currentState());
        StateMachine.Advance next = StateMachine.fire(spec, "MANAGER_REVIEW", "approve", Map.of("days", 2));
        assertTrue(next.ended());
        assertEquals(WorkflowOutcome.APPROVED, next.outcome());
        assertEquals(List.of("approve", "short"), next.steps().stream().map(StateMachine.Step::transitionCode).toList());
        assertTrue(next.steps().get(1).automatic());
    }

    @Test
    void longLeaveBranchesToDirector() {
        WorkflowSpec spec = WorkflowSpec.parse(leaveApproval());
        StateMachine.Advance next = StateMachine.fire(spec, "MANAGER_REVIEW", "approve", Map.of("days", 10));
        assertFalse(next.ended());
        assertEquals("DIRECTOR_REVIEW", next.currentState());
    }

    @Test
    void unknownTransitionOrWrongStateIsRejected() {
        WorkflowSpec spec = WorkflowSpec.parse(leaveApproval());
        assertThrows(NotFoundException.class, () -> StateMachine.fire(spec, "MANAGER_REVIEW", "teleport", Map.of()));
        assertThrows(vn.drwp.platform.kernel.error.InvalidStateException.class,
                () -> StateMachine.fire(spec, "CHECK_DAYS", "short", Map.of()));
    }

    @Test
    void guardOnTaskTransitionBlocksAction() {
        Map<String, Object> raw = new HashMap<>(leaveApproval());
        List<Object> transitions = new ArrayList<>((List<?>) raw.get("transitions"));
        transitions.set(0, Map.of("code", "approve", "from", "MANAGER_REVIEW", "to", "CHECK_DAYS", "permission", "APPROVE",
                "guard", Map.of("field", "attachments", "op", "notEmpty")));
        raw.put("transitions", transitions);
        WorkflowSpec spec = WorkflowSpec.parse(raw);
        assertThrows(StateMachine.GuardRejectedException.class, () -> StateMachine.fire(spec, "MANAGER_REVIEW", "approve", Map.of()));
        assertEquals(List.of("reject", "return", "escalate"),
                StateMachine.available(spec, "MANAGER_REVIEW", Map.of()).stream().map(TransitionDef::code).toList());
    }

    @Test
    void staticValidationCatchesBrokenGraphs() {
        ValidationFailedException e = assertThrows(ValidationFailedException.class, () -> WorkflowSpec.parse(Map.of(
                "initialState", "A",
                "states", List.of(
                        Map.of("code", "A", "type", "TASK", "assignment", Map.of("type", "ROLE", "value", "CLERK")),
                        Map.of("code", "D1", "type", "DECISION"),
                        Map.of("code", "D2", "type", "DECISION"),
                        Map.of("code", "ORPHAN", "type", "END", "outcome", "APPROVED")),
                "transitions", List.of(
                        Map.of("code", "go", "from", "A", "to", "D1", "permission", "APPROVE"),
                        Map.of("code", "loop", "from", "D1", "to", "D2"),
                        Map.of("code", "back", "from", "D2", "to", "D1")))));
        List<String> codes = e.violations().stream().map(Violation::code).toList();
        assertTrue(codes.contains("UNREACHABLE"), codes.toString());
        assertTrue(codes.contains("NO_END"), codes.toString());
        assertTrue(codes.contains("DECISION_CYCLE"), codes.toString());
    }

    @Test
    void taskTransitionsNeedPermissionAndDecisionsNeedDefault() {
        ValidationFailedException e = assertThrows(ValidationFailedException.class, () -> WorkflowSpec.parse(Map.of(
                "initialState", "A",
                "states", List.of(
                        Map.of("code", "A", "type", "TASK", "assignment", Map.of("type", "SUBMITTER")),
                        Map.of("code", "D", "type", "DECISION"),
                        Map.of("code", "E", "type", "END", "outcome", "APPROVED")),
                "transitions", List.of(
                        Map.of("code", "go", "from", "A", "to", "D"),
                        Map.of("code", "maybe", "from", "D", "to", "E", "guard", Map.of("field", "x", "op", "eq", "value", 1))))));
        List<String> codes = e.violations().stream().map(Violation::code).toList();
        assertTrue(codes.contains("REQUIRED"), codes.toString());
        assertTrue(codes.contains("NO_DEFAULT"), codes.toString());
    }

    @Test
    void assignmentResolution() {
        assertEquals(new AssignmentRule.Assignee("USER", "alice"),
                new AssignmentRule(AssignmentRule.Type.SUBMITTER, null).resolve("alice", Map.of()));
        assertEquals(new AssignmentRule.Assignee("USER", "bob"),
                new AssignmentRule(AssignmentRule.Type.FIELD, "approver").resolve("alice", Map.of("approver", "bob")));
        assertThrows(IllegalStateException.class,
                () -> new AssignmentRule(AssignmentRule.Type.FIELD, "approver").resolve("alice", Map.of()));
    }
}
