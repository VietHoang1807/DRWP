package vn.drwp.workflow.domain;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import vn.drwp.platform.kernel.condition.Condition;
import vn.drwp.platform.kernel.condition.ConditionException;
import vn.drwp.platform.kernel.condition.ConditionParser;
import vn.drwp.platform.kernel.condition.ConditionWriter;
import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.kernel.error.Violation;
import vn.drwp.platform.kernel.security.Action;

/**
 * Định nghĩa quy trình (phiên bản). Parse từ JSON và kiểm tra tĩnh: tham chiếu, khả năng đạt tới,
 * chu trình DECISION, timeout, mặc định của DECISION.
 */
public record WorkflowSpec(String initialState, Map<String, StateDef> states, List<TransitionDef> transitions) {

    public static final int MAX_STATES = 50;
    public static final int MAX_TRANSITIONS = 200;
    private static final Pattern STATE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,49}$");
    private static final Pattern TRANSITION_CODE = Pattern.compile("^[a-z][a-zA-Z0-9_]{0,49}$");
    private static final Duration MIN_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration MAX_TIMEOUT = Duration.ofDays(365);

    public WorkflowSpec {
        states = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(states));
        transitions = List.copyOf(transitions);
    }

    public StateDef state(String code) {
        StateDef s = states.get(code);
        if (s == null) {
            throw new IllegalArgumentException("Unknown state " + code);
        }
        return s;
    }

    public List<TransitionDef> transitionsFrom(String state) {
        return transitions.stream().filter(t -> t.from().equals(state)).toList();
    }

    @SuppressWarnings("unchecked")
    public static WorkflowSpec parse(Map<String, Object> raw) {
        List<Violation> v = new ArrayList<>();
        if (raw == null) {
            throw ValidationFailedException.single("spec", "REQUIRED", "spec is required");
        }
        for (String k : raw.keySet()) {
            if (!Set.of("initialState", "states", "transitions").contains(k)) {
                v.add(new Violation(k, "UNKNOWN_KEY", "Unsupported key"));
            }
        }
        Map<String, StateDef> states = new LinkedHashMap<>();
        List<Object> rawStates = raw.get("states") instanceof List<?> l ? (List<Object>) l : List.of();
        if (rawStates.isEmpty() || rawStates.size() > MAX_STATES) {
            v.add(new Violation("states", "SIZE", "1.." + MAX_STATES + " states required"));
        }
        for (int i = 0; i < rawStates.size(); i++) {
            String p = "states[" + i + "]";
            if (!(rawStates.get(i) instanceof Map<?, ?> m)) {
                v.add(new Violation(p, "TYPE", "State must be an object"));
                continue;
            }
            StateDef s = parseState((Map<String, Object>) m, p, v);
            if (s != null && states.putIfAbsent(s.code(), s) != null) {
                v.add(new Violation(p + ".code", "DUPLICATE", "Duplicate state " + s.code()));
            }
        }
        List<TransitionDef> transitions = new ArrayList<>();
        List<Object> rawTransitions = raw.get("transitions") instanceof List<?> l ? (List<Object>) l : List.of();
        if (rawTransitions.size() > MAX_TRANSITIONS) {
            v.add(new Violation("transitions", "SIZE", "At most " + MAX_TRANSITIONS + " transitions"));
        }
        Set<String> seenTransitions = new HashSet<>();
        for (int i = 0; i < rawTransitions.size(); i++) {
            String p = "transitions[" + i + "]";
            if (!(rawTransitions.get(i) instanceof Map<?, ?> m)) {
                v.add(new Violation(p, "TYPE", "Transition must be an object"));
                continue;
            }
            TransitionDef t = parseTransition((Map<String, Object>) m, p, states, v);
            if (t != null) {
                if (!seenTransitions.add(t.from() + "/" + t.code())) {
                    v.add(new Violation(p + ".code", "DUPLICATE", "Duplicate transition " + t.code() + " from " + t.from()));
                }
                transitions.add(t);
            }
        }
        String initial = raw.get("initialState") instanceof String s ? s : null;
        if (initial == null || !states.containsKey(initial)) {
            v.add(new Violation("initialState", "UNKNOWN_STATE", "initialState must reference a state"));
        } else if (states.get(initial).type() == StateType.END) {
            v.add(new Violation("initialState", "INVALID", "initialState cannot be an END state"));
        }
        if (initial != null && states.containsKey(initial)) {
            checkGraph(initial, states, transitions, v);
        }
        if (!v.isEmpty()) {
            throw new ValidationFailedException(v);
        }
        return new WorkflowSpec(initial, states, transitions);
    }

    @SuppressWarnings("unchecked")
    private static StateDef parseState(Map<String, Object> m, String p, List<Violation> v) {
        for (String k : m.keySet()) {
            if (!Set.of("code", "name", "type", "assignment", "timeout", "onTimeout", "outcome").contains(k)) {
                v.add(new Violation(p + "." + k, "UNKNOWN_KEY", "Unsupported state key"));
            }
        }
        String code = m.get("code") instanceof String s ? s : null;
        if (code == null || !STATE_CODE.matcher(code).matches()) {
            v.add(new Violation(p + ".code", "FORMAT", "State code must match " + STATE_CODE.pattern()));
            return null;
        }
        StateType type;
        try {
            type = StateType.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            v.add(new Violation(p + ".type", "UNKNOWN_TYPE", "type must be TASK, DECISION or END"));
            return null;
        }
        String name = m.get("name") instanceof String s ? s : code;
        AssignmentRule assignment = null;
        Duration timeout = null;
        String onTimeout = null;
        WorkflowOutcome outcome = null;
        switch (type) {
            case TASK -> {
                if (m.get("assignment") instanceof Map<?, ?> a) {
                    try {
                        AssignmentRule.Type at = AssignmentRule.Type.valueOf(String.valueOf(a.get("type")).toUpperCase(Locale.ROOT));
                        assignment = new AssignmentRule(at, a.get("value") == null ? null : String.valueOf(a.get("value")));
                    } catch (IllegalArgumentException e) {
                        v.add(new Violation(p + ".assignment", "INVALID", e.getMessage()));
                    }
                } else {
                    v.add(new Violation(p + ".assignment", "REQUIRED", "TASK state needs an assignment"));
                }
                if (m.get("timeout") instanceof String t) {
                    try {
                        timeout = Duration.parse(t);
                        if (timeout.compareTo(MIN_TIMEOUT) < 0 || timeout.compareTo(MAX_TIMEOUT) > 0) {
                            v.add(new Violation(p + ".timeout", "RANGE", "timeout must be between PT1M and P365D"));
                        }
                    } catch (DateTimeParseException e) {
                        v.add(new Violation(p + ".timeout", "FORMAT", "timeout must be an ISO-8601 duration, e.g. P3D"));
                    }
                    onTimeout = m.get("onTimeout") instanceof String o ? o : null;
                    if (onTimeout == null) {
                        v.add(new Violation(p + ".onTimeout", "REQUIRED", "timeout requires onTimeout transition"));
                    }
                }
            }
            case END -> {
                try {
                    outcome = WorkflowOutcome.valueOf(String.valueOf(m.get("outcome")).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    v.add(new Violation(p + ".outcome", "REQUIRED", "END state needs outcome APPROVED/REJECTED/RETURNED/CANCELLED"));
                }
            }
            case DECISION -> {
                if (m.containsKey("assignment") || m.containsKey("timeout")) {
                    v.add(new Violation(p, "NOT_APPLICABLE", "DECISION state has no assignment/timeout"));
                }
            }
        }
        return new StateDef(code, name, type, assignment, timeout, onTimeout, outcome);
    }

    private static TransitionDef parseTransition(Map<String, Object> m, String p, Map<String, StateDef> states, List<Violation> v) {
        for (String k : m.keySet()) {
            if (!Set.of("code", "from", "to", "label", "permission", "guard", "commentRequired").contains(k)) {
                v.add(new Violation(p + "." + k, "UNKNOWN_KEY", "Unsupported transition key"));
            }
        }
        String code = m.get("code") instanceof String s ? s : null;
        if (code == null || !TRANSITION_CODE.matcher(code).matches()) {
            v.add(new Violation(p + ".code", "FORMAT", "Transition code must match " + TRANSITION_CODE.pattern()));
            return null;
        }
        String from = m.get("from") instanceof String s ? s : null;
        String to = m.get("to") instanceof String s ? s : null;
        if (from == null || !states.containsKey(from)) {
            v.add(new Violation(p + ".from", "UNKNOWN_STATE", "from must reference a state"));
            return null;
        }
        if (to == null || !states.containsKey(to)) {
            v.add(new Violation(p + ".to", "UNKNOWN_STATE", "to must reference a state"));
            return null;
        }
        StateDef source = states.get(from);
        Condition guard = null;
        if (m.get("guard") != null) {
            try {
                guard = ConditionParser.parse(m.get("guard"));
            } catch (ConditionException e) {
                v.add(new Violation(p + ".guard", "INVALID_CONDITION", e.getMessage()));
            }
        }
        Action permission = null;
        if (source.type() == StateType.TASK) {
            if (m.get("permission") instanceof String perm) {
                try {
                    permission = Action.parse(perm);
                } catch (IllegalArgumentException e) {
                    v.add(new Violation(p + ".permission", "UNKNOWN_ACTION", "Unknown permission " + perm));
                }
            } else {
                v.add(new Violation(p + ".permission", "REQUIRED", "Transitions from TASK states need a permission (e.g. APPROVE)"));
            }
        } else if (source.type() == StateType.DECISION && m.containsKey("permission")) {
            v.add(new Violation(p + ".permission", "NOT_APPLICABLE", "DECISION transitions are automatic"));
        } else if (source.type() == StateType.END) {
            v.add(new Violation(p + ".from", "INVALID", "END states cannot have outgoing transitions"));
        }
        String label = m.get("label") instanceof String s ? s : code;
        return new TransitionDef(code, from, to, label, permission, guard, Boolean.TRUE.equals(m.get("commentRequired")));
    }

    private static void checkGraph(String initial, Map<String, StateDef> states, List<TransitionDef> transitions, List<Violation> v) {
        Map<String, List<TransitionDef>> out = new HashMap<>();
        transitions.forEach(t -> out.computeIfAbsent(t.from(), k -> new ArrayList<>()).add(t));
        for (StateDef s : states.values()) {
            List<TransitionDef> outgoing = out.getOrDefault(s.code(), List.of());
            if (s.type() == StateType.TASK && outgoing.isEmpty()) {
                v.add(new Violation("states." + s.code(), "DEAD_END", "TASK state has no outgoing transition"));
            }
            if (s.type() == StateType.DECISION) {
                if (outgoing.isEmpty() || outgoing.get(outgoing.size() - 1).guard() != null) {
                    v.add(new Violation("states." + s.code(), "NO_DEFAULT", "DECISION needs a last transition without guard (default)"));
                }
                if (outgoing.stream().filter(t -> t.guard() == null).count() > 1) {
                    v.add(new Violation("states." + s.code(), "MULTIPLE_DEFAULTS", "DECISION may have only one default transition"));
                }
            }
            if (s.onTimeout() != null && outgoing.stream().noneMatch(t -> t.code().equals(s.onTimeout()))) {
                v.add(new Violation("states." + s.code() + ".onTimeout", "UNKNOWN_TRANSITION", "onTimeout must be a transition from this state"));
            }
        }
        // khả năng đạt tới từ initial
        Set<String> reached = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(initial));
        while (!queue.isEmpty()) {
            String s = queue.poll();
            if (reached.add(s)) {
                out.getOrDefault(s, List.of()).forEach(t -> queue.add(t.to()));
            }
        }
        for (String s : states.keySet()) {
            if (!reached.contains(s)) {
                v.add(new Violation("states." + s, "UNREACHABLE", "State is not reachable from initialState"));
            }
        }
        if (reached.stream().noneMatch(s -> states.get(s).type() == StateType.END)) {
            v.add(new Violation("states", "NO_END", "No END state is reachable"));
        }
        // không có chu trình chỉ gồm DECISION (vòng lặp tự động vô hạn)
        Map<String, Integer> color = new HashMap<>();
        for (String s : states.keySet()) {
            if (states.get(s).type() == StateType.DECISION && hasDecisionCycle(s, states, out, color)) {
                v.add(new Violation("states." + s, "DECISION_CYCLE", "Automatic DECISION states form a cycle"));
                break;
            }
        }
    }

    private static boolean hasDecisionCycle(String s, Map<String, StateDef> states, Map<String, List<TransitionDef>> out,
                                            Map<String, Integer> color) {
        Integer c = color.get(s);
        if (c != null) {
            return c == 1;
        }
        color.put(s, 1);
        for (TransitionDef t : out.getOrDefault(s, List.of())) {
            if (states.get(t.to()).type() == StateType.DECISION && hasDecisionCycle(t.to(), states, out, color)) {
                return true;
            }
        }
        color.put(s, 2);
        return false;
    }

    /** Dạng cây chuẩn hóa để lưu. */
    public Map<String, Object> toTree() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("initialState", initialState);
        List<Map<String, Object>> ss = new ArrayList<>();
        for (StateDef s : states.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.code());
            m.put("name", s.name());
            m.put("type", s.type().name());
            if (s.assignment() != null) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("type", s.assignment().type().name());
                if (s.assignment().value() != null) {
                    a.put("value", s.assignment().value());
                }
                m.put("assignment", a);
            }
            if (s.timeout() != null) {
                m.put("timeout", s.timeout().toString());
                m.put("onTimeout", s.onTimeout());
            }
            if (s.outcome() != null) {
                m.put("outcome", s.outcome().name());
            }
            ss.add(m);
        }
        root.put("states", ss);
        List<Map<String, Object>> ts = new ArrayList<>();
        for (TransitionDef t : transitions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.code());
            m.put("from", t.from());
            m.put("to", t.to());
            m.put("label", t.label());
            if (t.permission() != null) {
                m.put("permission", t.permission().name());
            }
            if (t.guard() != null) {
                m.put("guard", ConditionWriter.toTree(t.guard()));
            }
            if (t.commentRequired()) {
                m.put("commentRequired", true);
            }
            ts.add(m);
        }
        root.put("transitions", ts);
        return root;
    }
}
