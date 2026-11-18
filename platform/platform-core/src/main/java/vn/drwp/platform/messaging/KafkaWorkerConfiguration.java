package vn.drwp.platform.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;

/** Retry có giới hạn rồi đẩy DLT ({@code <topic>-dlt}); vi phạm tenant đẩy DLT ngay, không retry. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRuntimeRole(RuntimeRole.EVENT_WORKER)
class KafkaWorkerConfiguration {

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(TenantIsolationException.class, IllegalArgumentException.class);
        return handler;
    }
}
