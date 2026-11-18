package vn.drwp.it;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import vn.drwp.platform.json.Json;

/**
 * Cơ sở cho test tích hợp: Spring context đầy đủ, MockMvc qua chuỗi Spring Security thật, token dev HS256.
 * Mỗi test tự tạo tenant riêng (tên ngẫu nhiên) nên các test chia sẻ được context.
 */
@SpringBootTest(properties = {
        "drwp.runtime.roles=api,scheduled-worker",
        "drwp.tenancy.control-plane.cluster=cluster-a",
        "drwp.tenancy.default-cluster=cluster-a",
        "drwp.security.dev.enabled=true",
        "drwp.security.dev.secret=integration-test-secret-0123456789-abcdefgh",
        "drwp.security.dev.issuer=drwp-test",
        "drwp.file.base-dir=target/test-files",
        "drwp.events.replay-interval=PT1H",
        "drwp.events.replay-older-than=1m",
        "drwp.workflow.timeout-scan-interval=PT1H",
        "drwp.tenancy.placement-cache-ttl=1s"
})
@EnabledIf("vn.drwp.it.TestDatabases#available")
abstract class IntegrationTestSupport {

    @DynamicPropertySource
    static void databases(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry);
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    Json json;

    MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static String uniqueTenant(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    record Response(int status, Map<String, Object> body, List<Object> list, String raw) {

        @SuppressWarnings("unchecked")
        Object at(String path) {
            Object cur = body;
            for (String seg : path.split("\\.")) {
                if (cur instanceof Map<?, ?> m) {
                    cur = m.get(seg);
                } else if (cur instanceof List<?> l) {
                    cur = l.get(Integer.parseInt(seg));
                } else {
                    return null;
                }
            }
            return cur;
        }

        String str(String path) {
            Object v = at(path);
            return v == null ? null : String.valueOf(v);
        }
    }

    String token(String subject, boolean platformAdmin) throws Exception {
        MvcResult r = mvc.perform(request(HttpMethod.POST, "/dev/token").contentType(MediaType.APPLICATION_JSON)
                .content(json.write(Map.of("subject", subject, "username", subject, "platformAdmin", platformAdmin)))).andReturn();
        return String.valueOf(json.readMap(r.getResponse().getContentAsString()).get("access_token"));
    }

    Response call(HttpMethod method, String path, String token, String tenant, Object body) throws Exception {
        MockHttpServletRequestBuilder req = request(method, path).accept(MediaType.APPLICATION_JSON);
        return execute(req, token, tenant, body);
    }

    Response upload(String path, String token, String tenant, MockMultipartFile file) throws Exception {
        return execute(multipart(path).file(file), token, tenant, null);
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> Response execute(B req, String token, String tenant, Object body)
            throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (tenant != null) {
            req.header("X-Tenant-Id", tenant);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.write(body));
        }
        MvcResult r = mvc.perform(req).andReturn();
        String raw = r.getResponse().getContentAsString();
        Map<String, Object> map = null;
        List<Object> list = null;
        if (raw != null && !raw.isBlank()) {
            String t = raw.trim();
            if (t.startsWith("{")) {
                map = json.readMap(t);
            } else if (t.startsWith("[")) {
                list = json.readList(t);
            }
        }
        return new Response(r.getResponse().getStatus(), map, list, raw);
    }

    /** Provision tenant với admin; trả về token admin. */
    String provision(String tenantId, String adminSubject, String cluster) throws Exception {
        String platformAdmin = token("platform-root", true);
        Response r = call(HttpMethod.POST, "/api/platform/tenants", platformAdmin, null, Map.of(
                "tenantId", tenantId, "displayName", "Tenant " + tenantId, "clusterId", cluster,
                "adminSubject", adminSubject, "adminDisplayName", "Admin " + tenantId));
        if (r.status() != 201) {
            throw new AssertionError("Provisioning failed: " + r.status() + " " + r.raw());
        }
        return token(adminSubject, false);
    }
}
