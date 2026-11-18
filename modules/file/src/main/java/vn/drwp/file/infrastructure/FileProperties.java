package vn.drwp.file.infrastructure;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("drwp.file")
public record FileProperties(
        /** filesystem | minio */
        @DefaultValue("filesystem") String storage,
        @DefaultValue("./data/files") String baseDir,
        @DefaultValue("20MB") DataSize maxSize,
        @DefaultValue({"application/pdf", "image/png", "image/jpeg", "text/plain",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"}) Set<String> allowedContentTypes,
        Minio minio) {

    public record Minio(String endpoint, String accessKey, String secretKey, @DefaultValue("drwp-files") String bucket) {
    }
}
