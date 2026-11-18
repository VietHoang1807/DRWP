package vn.drwp.workflow.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import vn.drwp.platform.kernel.condition.ConditionEvaluator;
import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.platform.kernel.error.NotFoundException;

/**
 * Bộ máy trạng thái thuần: tính bước kế tiếp từ phiên bản quy trình + dữ liệu hồ sơ. Không I/O, xác định.
 * Bước TASK dừng chờ người; DECISION đi tiếp tự động; END kết thúc.
 */
public final class StateMachine {

    public static final int MAX_AUTOMATIC_HOPS = 20;

    public record Step(String from, String to, String transitionCode, boolean automatic) {
    }

    /** Kết quả: chuỗi bước đã đi, trạng thái hiện tại; nếu ended thì có outcome, nếu không thì đang chờ ở TASK. */
    public record Advance(List<Step> steps, String currentState, boolean ended, WorkflowOutcome outcome) {
    }

    private StateMachine() {
    }

    public static Advance start(WorkflowSpec spec, Map<String, Object> data) {
        List<Step> steps = new ArrayList<>();
        return enter(spec, spec.initialState(), data, steps);
    }

    /** Người dùng (hoặc timeout) kích hoạt {@code transitionCode} khi instance đang ở {@code currentState}. */
    public static Advance fire(WorkflowSpec spec, String currentState, String transitionCode, Map<String, Object> data) {
        TransitionDef t = transition(spec, currentState, transitionCode);
        if (!ConditionEvaluator.evaluate(t.guard(), data)) {
            throw new GuardRejectedException("Transition " + transitionCode + " is not allowed for the current record data");
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(currentState, t.to(), t.code(), false));
        return enter(spec, t.to(), data, steps);
    }

    public static TransitionDef transition(WorkflowSpec spec, String currentState, String transitionCode) {
        StateDef state = spec.state(currentState);
        if (state.type() != StateType.TASK) {
            throw new InvalidStateException("State " + currentState + " does not accept manual transitions");
        }
        return spec.transitionsFrom(currentState).stream()
                .filter(t -> t.code().equals(transitionCode))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Transition", transitionCode + " from " + currentState));
    }

    /** Các transition người dùng có thể chọn tại trạng thái (guard đúng với dữ liệu hiện tại). */
    public static List<TransitionDef> available(WorkflowSpec spec, String currentState, Map<String, Object> data) {
        return spec.transitionsFrom(currentState).stream()
                .filter(t -> ConditionEvaluator.evaluate(t.guard(), data))
                .toList();
    }

    private static Advance enter(WorkflowSpec spec, String state, Map<String, Object> data, List<Step> steps) {
        String current = state;
        for (int hop = 0; hop <= MAX_AUTOMATIC_HOPS; hop++) {
            StateDef def = spec.state(current);
            switch (def.type()) {
                case END -> {
                    return new Advance(List.copyOf(steps), current, true, def.outcome());
                }
                case TASK -> {
                    return new Advance(List.copyOf(steps), current, false, null);
                }
                case DECISION -> {
                    TransitionDef chosen = null;
                    for (TransitionDef t : spec.transitionsFrom(current)) {
                        if (ConditionEvaluator.evaluate(t.guard(), data)) {
                            chosen = t;
                            break;
                        }
                    }
                    if (chosen == null) {
                        throw new InvalidStateException("DECISION " + current + " has no matching transition");
                    }
                    steps.add(new Step(current, chosen.to(), chosen.code(), true));
                    current = chosen.to();
                }
            }
        }
        throw new InvalidStateException("Too many automatic transitions (possible loop)");
    }

    public static class GuardRejectedException extends InvalidStateException {
        public GuardRejectedException(String message) {
            super(message);
        }
    }
}
