package vn.drwp.metadata.domain;

public enum FieldType {
    TEXT, TEXTAREA, EMAIL, NUMBER, INTEGER, BOOLEAN, DATE, DATETIME, SELECT, MULTISELECT,
    /** Danh sách ID tệp đính kèm (UUID). */
    FILE,
    /** Subject của người dùng (dùng cho phân công theo trường trong workflow). */
    USER
}
