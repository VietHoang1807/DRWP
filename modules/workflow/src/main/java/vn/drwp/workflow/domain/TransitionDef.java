package vn.drwp.workflow.domain;

import vn.drwp.platform.kernel.condition.Condition;
import vn.drwp.platform.kernel.security.Action;

/**
 * Chuyển trạng thái. Với TASK: người dùng kích hoạt, cần {@code permission} trên {@code wf:<CODE>}, assignment
 * và guard (nếu có). Với DECISION: tự động, guard đầu tiên thỏa thắng; transition không guard là mặc định (cuối cùng).
 */
public record TransitionDef(String code, String from, String to, String label, Action permission, Condition guard,
                            boolean commentRequired) {
}
