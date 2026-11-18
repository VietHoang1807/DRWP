package vn.drwp.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimeRoleConditionTest {

    @Test
    void parsesRoleList() {
        assertThat(RuntimeRoleCondition.enabledRoles(" API , event-worker,")).containsExactlyInAnyOrder("api", "event-worker");
        assertThat(RuntimeRoleCondition.enabledRoles(RuntimeRoleCondition.DEFAULT)).hasSize(3);
    }
}
