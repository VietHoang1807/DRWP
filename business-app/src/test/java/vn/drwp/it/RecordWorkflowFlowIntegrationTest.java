package vn.drwp.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Luồng xuyên module chính (ADR 024 tầng 3): cấu hình vai trò + biểu mẫu + quy trình, nộp hồ sơ,
 * phân nhánh, duyệt, trả lại/nộp lại, phiên bản V2 không ảnh hưởng instance đang chạy, audit DENIED,
 * thông báo, tệp đính kèm, tìm kiếm theo whitelist, chống BOLA.
 */
@EnabledIf(value = "vn.drwp.it.TestDatabases#available", disabledReason = "Cần Docker hoặc DRWP_TEST_DB_A_URL")
class RecordWorkflowFlowIntegrationTest extends IntegrationTestSupport {

    String tenant;
    String admin;
    String alice;   // nhân viên
    String bob;     // trưởng phòng
    String carol;   // giám đốc
    String mallory; // thành viên không có quyền

    @BeforeEach
    void configureTenant() throws Exception {
        tenant = uniqueTenant("flow");
        admin = provision(tenant, "admin-" + tenant, "cluster-a");

        role("EMPLOYEE", List.of(grant("record:LEAVE", List.of("CREATE"))));
        role("MANAGER", List.of(grant("wf:LEAVE_APPROVAL", List.of("VIEW", "APPROVE", "REJECT")),
                grant("record:LEAVE", List.of("VIEW"))));
        role("DIRECTOR", List.of(grant("wf:LEAVE_APPROVAL", List.of("APPROVE", "REJECT"))));
        member("alice", List.of("EMPLOYEE"));
        member("bob", List.of("MANAGER"));
        member("carol", List.of("DIRECTOR"));
        member("mallory", List.of());
        alice = token("alice", false);
        bob = token("bob", false);
        carol = token("carol", false);
        mallory = token("mallory", false);

        Response form = call(HttpMethod.POST, "/api/metadata/forms", admin, tenant, Map.of(
                "code", "LEAVE", "name", "Đơn nghỉ phép", "schema", Map.of("fields", List.of(
                        Map.of("key", "leaveType", "label", "Loại nghỉ", "type", "SELECT", "required", true, "filterable", true,
                                "options", List.of("ANNUAL", "SICK")),
                        Map.of("key", "days", "label", "Số ngày", "type", "NUMBER", "required", true, "min", 0.5, "max", 30),
                        Map.of("key", "reason", "label", "Lý do", "type", "TEXTAREA",
                                "requiredIf", Map.of("field", "days", "op", "gt", "value", 3)),
                        Map.of("key", "attachments", "label", "Đính kèm", "type", "FILE")))));
        assertThat(form.status()).as(form.raw()).isEqualTo(201);
        assertThat(call(HttpMethod.POST, "/api/metadata/forms/LEAVE/versions/1/publish", admin, tenant, null).status()).isEqualTo(200);

        Response wf = call(HttpMethod.POST, "/api/workflow/definitions", admin, tenant, Map.of(
                "code", "LEAVE_APPROVAL", "name", "Duyệt nghỉ phép", "formCode", "LEAVE", "spec", leaveSpec()));
        assertThat(wf.status()).as(wf.raw()).isEqualTo(201);
        assertThat(call(HttpMethod.POST, "/api/workflow/definitions/LEAVE_APPROVAL/versions/1/publish", admin, tenant, null).status())
                .isEqualTo(200);
    }

    static Map<String, Object> leaveSpec() {
        return Map.of(
                "initialState", "MANAGER_REVIEW",
                "states", List.of(
                        Map.of("code", "MANAGER_REVIEW", "name", "Trưởng phòng duyệt", "type", "TASK",
                                "assignment", Map.of("type", "ROLE", "value", "MANAGER")),
                        Map.of("code", "CHECK_DAYS", "type", "DECISION"),
                        Map.of("code", "DIRECTOR_REVIEW", "name", "Giám đốc duyệt", "type", "TASK",
                                "assignment", Map.of("type", "ROLE", "value", "DIRECTOR")),
                        Map.of("code", "APPROVED", "type", "END", "outcome", "APPROVED"),
                        Map.of("code", "REJECTED", "type", "END", "outcome", "REJECTED"),
                        Map.of("code", "RETURNED", "type", "END", "outcome", "RETURNED")),
                "transitions", List.of(
                        Map.of("code", "approve", "from", "MANAGER_REVIEW", "to", "CHECK_DAYS", "permission", "APPROVE"),
                        Map.of("code", "reject", "from", "MANAGER_REVIEW", "to", "REJECTED", "permission", "REJECT", "commentRequired", true),
                        Map.of("code", "return", "from", "MANAGER_REVIEW", "to", "RETURNED", "permission", "REJECT", "commentRequired", true),
                        Map.of("code", "long", "from", "CHECK_DAYS", "to", "DIRECTOR_REVIEW",
                                "guard", Map.of("field", "days", "op", "gt", "value", 5)),
                        Map.of("code", "short", "from", "CHECK_DAYS", "to", "APPROVED"),
                        Map.of("code", "approve", "from", "DIRECTOR_REVIEW", "to", "APPROVED", "permission", "APPROVE"),
                        Map.of("code", "reject", "from", "DIRECTOR_REVIEW", "to", "REJECTED", "permission", "REJECT")));
    }

    @Test
    void longLeaveGoesThroughManagerAndDirector() throws Exception {
        Response created = call(HttpMethod.POST, "/api/records", alice, tenant, Map.of("formCode", "LEAVE", "submit", true,
                "data", Map.of("leaveType", "ANNUAL", "days", 10, "reason", "Về quê")));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        String recordId = created.str("id");
        assertThat(created.str("status")).isEqualTo("SUBMITTED");

        String managerTask = awaitSingleTask(bob);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(call(HttpMethod.GET, "/api/records/" + recordId, alice, tenant, null).str("status")).isEqualTo("IN_PROGRESS"));

        // mallory: không được giao việc => 403 và có audit DENIED
        Response denied = call(HttpMethod.POST, "/api/workflow/tasks/" + managerTask + "/transitions/approve", mallory, tenant, Map.of());
        assertThat(denied.status()).isEqualTo(403);
        // carol có APPROVE nhưng task giao cho MANAGER => 403
        assertThat(call(HttpMethod.POST, "/api/workflow/tasks/" + managerTask + "/transitions/approve", carol, tenant, Map.of()).status())
                .isEqualTo(403);

        // V2 của quy trình phát hành trong lúc instance V1 đang chạy
        assertThat(call(HttpMethod.POST, "/api/workflow/definitions/LEAVE_APPROVAL/versions", admin, tenant, null).status()).isEqualTo(201);
        assertThat(call(HttpMethod.POST, "/api/workflow/definitions/LEAVE_APPROVAL/versions/2/publish", admin, tenant, null).status())
                .isEqualTo(200);

        Response approved = call(HttpMethod.POST, "/api/workflow/tasks/" + managerTask + "/transitions/approve", bob, tenant,
                Map.of("comment", "Đồng ý"));
        assertThat(approved.status()).as(approved.raw()).isEqualTo(200);
        // xử lý lại cùng task => 409
        assertThat(call(HttpMethod.POST, "/api/workflow/tasks/" + managerTask + "/transitions/approve", bob, tenant, Map.of()).status())
                .isEqualTo(409);

        String directorTask = awaitSingleTask(carol);
        Response instance = call(HttpMethod.GET, "/api/workflow/tasks/" + directorTask, carol, tenant, null);
        assertThat(instance.str("task.stateCode")).isEqualTo("DIRECTOR_REVIEW");
        assertThat(call(HttpMethod.POST, "/api/workflow/tasks/" + directorTask + "/transitions/approve", carol, tenant, Map.of()).status())
                .isEqualTo(200);

        Response record = call(HttpMethod.GET, "/api/records/" + recordId, alice, tenant, null);
        assertThat(record.str("status")).isEqualTo("APPROVED");
        Response instances = call(HttpMethod.GET, "/api/workflow/instances?recordId=" + recordId, alice, tenant, null);
        assertThat(((Map<?, ?>) instances.list().getFirst()).get("versionNo")).isEqualTo(1); // V1 vẫn được dùng
        assertThat(((Map<?, ?>) instances.list().getFirst()).get("outcome")).isEqualTo("APPROVED");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Response n = call(HttpMethod.GET, "/api/notifications", alice, tenant, null);
            assertThat(n.raw()).contains("WORKFLOW_APPROVED");
            Response audit = call(HttpMethod.GET, "/api/audit/events?outcome=DENIED", admin, tenant, null);
            assertThat(((Number) audit.at("total")).longValue()).isGreaterThanOrEqualTo(2L);
        });

        // BOLA: mallory không xem được hồ sơ của alice; bob (VIEW) xem được
        assertThat(call(HttpMethod.GET, "/api/records/" + recordId, mallory, tenant, null).status()).isEqualTo(404);
        assertThat(call(HttpMethod.GET, "/api/records/" + recordId, bob, tenant, null).status()).isEqualTo(200);
        // mallory không đọc được audit
        assertThat(call(HttpMethod.GET, "/api/audit/events", mallory, tenant, null).status()).isEqualTo(403);
    }

    @Test
    void returnedRecordCanBeFixedAndResubmitted() throws Exception {
        Response created = call(HttpMethod.POST, "/api/records", alice, tenant, Map.of("formCode", "LEAVE", "submit", true,
                "data", Map.of("leaveType", "SICK", "days", 2)));
        String recordId = created.str("id");
        String task = awaitSingleTask(bob);

        Response noComment = call(HttpMethod.POST, "/api/workflow/tasks/" + task + "/transitions/return", bob, tenant, Map.of());
        assertThat(noComment.status()).isEqualTo(422);
        assertThat(call(HttpMethod.POST, "/api/workflow/tasks/" + task + "/transitions/return", bob, tenant,
                Map.of("comment", "Bổ sung giấy khám bệnh")).status()).isEqualTo(200);
        Response returned = call(HttpMethod.GET, "/api/records/" + recordId, alice, tenant, null);
        assertThat(returned.str("status")).isEqualTo("RETURNED");

        // validate theo đúng phiên bản biểu mẫu: days > 3 bắt buộc reason
        Response invalid = call(HttpMethod.PUT, "/api/records/" + recordId, alice, tenant,
                Map.of("data", Map.of("leaveType", "SICK", "days", 4)));
        assertThat(invalid.status()).isEqualTo(200); // lưu nháp: chưa kiểm tra required
        Response submitInvalid = call(HttpMethod.POST, "/api/records/" + recordId + "/submit", alice, tenant, Map.of());
        assertThat(submitInvalid.status()).isEqualTo(422);
        assertThat(submitInvalid.raw()).contains("reason");

        call(HttpMethod.PUT, "/api/records/" + recordId, alice, tenant,
                Map.of("data", Map.of("leaveType", "SICK", "days", 2, "reason", "Đã bổ sung")));
        assertThat(call(HttpMethod.POST, "/api/records/" + recordId + "/submit", alice, tenant, Map.of()).status()).isEqualTo(200);
        String second = awaitSingleTask(bob);
        assertThat(second).isNotEqualTo(task);
        assertThat(call(HttpMethod.POST, "/api/workflow/tasks/" + second + "/transitions/approve", bob, tenant, Map.of()).status())
                .isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/records/" + recordId, alice, tenant, null).str("status")).isEqualTo("APPROVED");
        Response revisions = call(HttpMethod.GET, "/api/records/" + recordId + "/revisions", alice, tenant, null);
        assertThat(revisions.list().size()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void attachmentsFollowRecordPermissions() throws Exception {
        Response created = call(HttpMethod.POST, "/api/records", alice, tenant, Map.of("formCode", "LEAVE",
                "data", Map.of("leaveType", "ANNUAL", "days", 1)));
        String recordId = created.str("id");
        MockMultipartFile pdf = new MockMultipartFile("file", "giay-to.pdf", "application/pdf",
                "%PDF-1.7\n%test\n".getBytes(StandardCharsets.US_ASCII));
        Response uploaded = upload("/api/files?recordId=" + recordId, alice, tenant, pdf);
        assertThat(uploaded.status()).as(uploaded.raw()).isEqualTo(201);
        String fileId = uploaded.str("id");

        MockMultipartFile spoofed = new MockMultipartFile("file", "virus.pdf", "application/pdf", "MZ\u0090".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(upload("/api/files?recordId=" + recordId, alice, tenant, spoofed).status()).isEqualTo(422);
        assertThat(upload("/api/files?recordId=" + recordId, mallory, tenant, pdf).status()).isEqualTo(404);

        assertThat(call(HttpMethod.GET, "/api/files/" + fileId + "/content", alice, tenant, null).status()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/files/" + fileId + "/content", bob, tenant, null).status()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/files/" + fileId + "/content", mallory, tenant, null).status()).isEqualTo(404);
    }

    @Test
    void searchHonoursWhitelistAndOwnership() throws Exception {
        call(HttpMethod.POST, "/api/records", alice, tenant, Map.of("formCode", "LEAVE", "data", Map.of("leaveType", "ANNUAL", "days", 1)));
        call(HttpMethod.POST, "/api/records", alice, tenant, Map.of("formCode", "LEAVE", "data", Map.of("leaveType", "SICK", "days", 1)));
        Response byType = call(HttpMethod.GET, "/api/records?formCode=LEAVE&data.leaveType=ANNUAL", bob, tenant, null);
        assertThat(byType.status()).isEqualTo(200);
        assertThat(((Number) byType.at("total")).longValue()).isEqualTo(1L);
        assertThat(call(HttpMethod.GET, "/api/records?formCode=LEAVE&data.days=1", bob, tenant, null).status()).isEqualTo(400);
        Response malloryView = call(HttpMethod.GET, "/api/records?formCode=LEAVE", mallory, tenant, null);
        assertThat(((Number) malloryView.at("total")).longValue()).isZero();
        assertThat(call(HttpMethod.POST, "/api/records", mallory, tenant, Map.of("formCode", "LEAVE", "data", Map.of())).status())
                .isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private String awaitSingleTask(String token) {
        String[] holder = new String[1];
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Response inbox = call(HttpMethod.GET, "/api/workflow/tasks", token, tenant, null);
            assertThat(inbox.status()).isEqualTo(200);
            List<?> items = (List<?>) inbox.at("items");
            assertThat(items).hasSize(1);
            holder[0] = String.valueOf(((Map<?, ?>) items.getFirst()).get("id"));
        });
        return holder[0];
    }

    private static Map<String, Object> grant(String resource, List<String> allow) {
        return Map.of("resource", resource, "allow", allow);
    }

    private void role(String code, List<Map<String, Object>> grants) throws Exception {
        assertThat(call(HttpMethod.POST, "/api/identity/roles", admin, tenant, Map.of("code", code, "name", code)).status()).isEqualTo(201);
        Response r = call(HttpMethod.PUT, "/api/identity/roles/" + code + "/permissions", admin, tenant, grants);
        assertThat(r.status()).as(r.raw()).isEqualTo(200);
    }

    private void member(String subject, List<String> roles) throws Exception {
        Response r = call(HttpMethod.POST, "/api/identity/members", admin, tenant,
                Map.of("subject", subject, "displayName", subject, "roles", roles));
        assertThat(r.status()).as(r.raw()).isEqualTo(201);
    }
}
