package vn.drwp.record.api;

import java.util.UUID;

/** Contract cho File (dependency matrix: File -> Record, Direct). */
public interface RecordAttachmentPolicy {

    /** Người dùng được đính kèm tệp vào hồ sơ (chủ hồ sơ khi còn sửa được, hoặc có UPDATE). */
    boolean canAttach(UUID recordId, String subject);

    /** Người dùng được xem hồ sơ (chủ hồ sơ hoặc có VIEW trên record:&lt;FORM&gt;). */
    boolean canView(UUID recordId, String subject);
}
