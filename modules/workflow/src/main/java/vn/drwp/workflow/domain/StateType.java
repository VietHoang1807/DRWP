package vn.drwp.workflow.domain;

public enum StateType {
    /** Chờ người xử lý (tạo task). */
    TASK,
    /** Rẽ nhánh tự động theo guard (không tạo task). */
    DECISION,
    /** Kết thúc với outcome. */
    END
}
