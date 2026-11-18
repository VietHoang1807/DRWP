package vn.drwp.metadata.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import vn.drwp.platform.kernel.error.Violation;

/** Contract cho module Record (dependency matrix: Record -> Metadata, Direct). */
public interface FormDefinitionReader {

    /** Phiên bản PUBLISHED mới nhất của biểu mẫu. */
    Optional<PublishedForm> latestPublished(String formCode);

    /** Phiên bản cụ thể (PUBLISHED hoặc DEPRECATED). Hồ sơ luôn gắn phiên bản này (bất biến). */
    PublishedForm version(UUID formVersionId);

    /** @param full true: kiểm tra đầy đủ khi nộp; false: chỉ kiểu/ràng buộc của trường có giá trị (lưu nháp). */
    List<Violation> validate(UUID formVersionId, Map<String, Object> data, boolean full);

    /** Trường được phép lọc (whitelist) theo phiên bản mới nhất. */
    Set<String> filterableFields(String formCode);

    /** Trường nhạy cảm: ẩn khỏi kết quả danh sách. */
    Set<String> sensitiveFields(UUID formVersionId);

    record PublishedForm(UUID formVersionId, String formCode, String name, int versionNo, String status,
                         Map<String, Object> schema) {
    }
}
