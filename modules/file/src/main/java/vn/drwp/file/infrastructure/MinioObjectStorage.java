package vn.drwp.file.infrastructure;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

/** MinIO/S3 private bucket. Không phát presigned URL công khai; tải về đi qua API để kiểm quyền và audit. */
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(FileProperties.Minio cfg) {
        this.client = MinioClient.builder().endpoint(cfg.endpoint()).credentials(cfg.accessKey(), cfg.secretKey()).build();
        this.bucket = cfg.bucket();
    }

    public void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise MinIO bucket " + bucket, e);
        }
    }

    @Override
    public void put(String key, Path source, long size, String contentType) {
        try (InputStream in = Files.newInputStream(source)) {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(in, size, -1L)
                    .contentType(contentType).build());
        } catch (Exception e) {
            throw new IllegalStateException("Upload to object storage failed", e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Download from object storage failed", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Delete from object storage failed", e);
        }
    }
}
