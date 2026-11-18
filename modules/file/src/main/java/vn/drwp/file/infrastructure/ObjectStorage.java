package vn.drwp.file.infrastructure;

import java.io.InputStream;
import java.nio.file.Path;

/** Port lưu trữ đối tượng. Khóa đối tượng luôn có tiền tố tenant; bucket/thư mục private. */
public interface ObjectStorage {

    void put(String key, Path source, long size, String contentType);

    InputStream get(String key);

    void delete(String key);
}
