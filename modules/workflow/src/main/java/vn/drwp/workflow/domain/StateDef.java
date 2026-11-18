package vn.drwp.workflow.domain;

import java.time.Duration;

public record StateDef(String code, String name, StateType type, AssignmentRule assignment, Duration timeout,
                       String onTimeout, WorkflowOutcome outcome) {
}
