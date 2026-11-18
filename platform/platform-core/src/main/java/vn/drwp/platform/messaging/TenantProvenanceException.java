package vn.drwp.platform.messaging;

import vn.drwp.platform.kernel.error.TenantIsolationException;

/** Sự kiện Kafka có tenantId không khớp schema nguồn: đẩy DLT, không retry. */
public class TenantProvenanceException extends TenantIsolationException {
    public TenantProvenanceException(String message) {
        super(message);
    }
}
