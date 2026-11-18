package vn.drwp.tenant.application;

import java.util.List;

public record ProvisionTenantCommand(
        String tenantId,
        String displayName,
        String clusterId,
        List<String> complianceProfiles,
        String adminSubject,
        String adminDisplayName) {
}
