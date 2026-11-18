package vn.drwp.file.infrastructure;

import java.nio.file.Path;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.drwp.file.domain.ContentPolicy;
import vn.drwp.platform.tenancy.TenantSchemaModule;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
class FileConfiguration {

    @Bean
    TenantSchemaModule fileTenantSchema() {
        return new TenantSchemaModule("file", 50);
    }

    @Bean
    ObjectStorage objectStorage(FileProperties props) {
        if ("minio".equalsIgnoreCase(props.storage())) {
            if (props.minio() == null || props.minio().endpoint() == null) {
                throw new IllegalStateException("drwp.file.minio.* must be configured when drwp.file.storage=minio");
            }
            MinioObjectStorage storage = new MinioObjectStorage(props.minio());
            storage.ensureBucket();
            return storage;
        }
        return new FileSystemObjectStorage(Path.of(props.baseDir()));
    }

    @Bean
    ContentPolicy contentPolicy(FileProperties props) {
        return new ContentPolicy(props.maxSize().toBytes(), props.allowedContentTypes());
    }
}
