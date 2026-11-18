# Hướng dẫn sử dụng DRWP backend

Hướng dẫn đi qua một luồng hoàn chỉnh: dựng môi trường → tạo tenant → vai trò → biểu mẫu → quy trình →
nộp hồ sơ → duyệt → thông báo, audit, tệp đính kèm. Ví dụ dùng `curl` + `jq`.

## 0. Chuẩn bị

```bash
# podman compose -f infrastructure/compose/docker-compose.yml up -d postgres-a postgres-b
docker compose -f infrastructure/compose/docker-compose.yml up -d postgres-a postgres-b
mvn -q -DskipTests install
# profile dev: all-in-one (api + scheduled-worker), token HS256 qua /dev/token; thêm cluster-b để thử placement
mvn -pl business-app spring-boot:run -Dspring-boot.run.profiles=dev,cluster-b
```

Kiểm tra: `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`.

Hàm tiện ích cho shell:

```bash
API=http://localhost:8080
tok() { curl -s -X POST $API/dev/token -H 'Content-Type: application/json' \
          -d "{\"subject\":\"$1\",\"platformAdmin\":${2:-false}}" | jq -r .access_token; }
ROOT=$(tok platform-root true)   # quản trị nền tảng
ADMIN=$(tok admin-hcm)           # quản trị đơn vị (sẽ là admin của tenant)
ALICE=$(tok alice); BOB=$(tok bob); CAROL=$(tok carol)
T=so-noi-vu-hcm                  # tenant id
call() { # call METHOD PATH TOKEN [JSON]
  curl -s -X "$1" "$API$2" -H "Authorization: Bearer $3" -H "X-Tenant-Id: $T" \
       -H 'Content-Type: application/json' ${4:+-d "$4"}; echo; }
```

> Dùng Keycloak thay vì token dev: `docker compose ... up -d keycloak`, chạy app **không** có profile `dev`, lấy token bằng
> `curl -s -d client_id=drwp-cli -d grant_type=password -d username=alice -d password=password http://localhost:8180/realms/drwp/protocol/openid-connect/token | jq -r .access_token`.
> `subject` khi đó là `sub` của Keycloak (UUID trong `infrastructure/keycloak/drwp-realm.json`).

## 1. Provision tenant (quản trị nền tảng)

```bash
curl -s -X POST $API/api/platform/tenants -H "Authorization: Bearer $ROOT" -H 'Content-Type: application/json' -d '{
  "tenantId": "so-noi-vu-hcm", "displayName": "Sở Nội vụ TP.HCM",
  "clusterId": "cluster-b", "complianceProfiles": ["GOVERNMENT"],
  "adminSubject": "admin-hcm", "adminDisplayName": "Quản trị Sở"}' | jq
```

Kết quả: schema `tenant_so_noi_vu_hcm` được tạo **trên cụm B**, chạy migration của mọi module, tạo vai trò hệ thống
`TENANT_ADMIN` (mọi quyền trên `*`) và gán cho `admin-hcm`. Nếu lỗi giữa chừng, gọi lại cùng request để tiếp tục.

Các thao tác khác: `GET /api/platform/tenants`, `POST /api/platform/tenants/{id}/suspend|activate|migrate`.
Người dùng xem tenant của mình: `GET /api/me/tenants` (không cần `X-Tenant-Id`).

## 2. Vai trò và thành viên (quản trị đơn vị)

```bash
call POST /api/identity/roles $ADMIN '{"code":"EMPLOYEE","name":"Chuyên viên"}'
call PUT  /api/identity/roles/EMPLOYEE/permissions $ADMIN '[{"resource":"record:LEAVE","allow":["CREATE"]}]'

call POST /api/identity/roles $ADMIN '{"code":"MANAGER","name":"Trưởng phòng"}'
call PUT  /api/identity/roles/MANAGER/permissions $ADMIN '[
  {"resource":"wf:LEAVE_APPROVAL","allow":["VIEW","APPROVE","REJECT"]},
  {"resource":"record:LEAVE","allow":["VIEW"]}]'

call POST /api/identity/roles $ADMIN '{"code":"DIRECTOR","name":"Giám đốc"}'
call PUT  /api/identity/roles/DIRECTOR/permissions $ADMIN '[{"resource":"wf:LEAVE_APPROVAL","allow":["APPROVE","REJECT"]}]'

call POST /api/identity/members $ADMIN '{"subject":"alice","displayName":"Alice","roles":["EMPLOYEE"]}'
call POST /api/identity/members $ADMIN '{"subject":"bob","displayName":"Bob","roles":["MANAGER"]}'
call POST /api/identity/members $ADMIN '{"subject":"carol","displayName":"Carol","roles":["DIRECTOR"]}'
```

- Bảng action/bit: `GET /api/identity/actions` (VIEW=1, CREATE=2, UPDATE=4, DELETE=8, APPROVE=16, REJECT=32, EXPORT=64,
  ASSIGN=128, SHARE=256, CONFIGURE=512, AUDIT=1024, MANAGE=2048).
- Resource: `*`, `<type>`, `<type>:*`, `<type>:<CODE>`. Các type đang dùng: `identity`, `metadata`, `workflow`, `audit`,
  `record:<FORM>`, `wf:<WORKFLOW>`. Có thể đặt `deny` để cấm tường minh (deny thắng allow).
- Người cấp chỉ cấp được các bit **chính mình đang có** (chống leo thang).
- Kiểm tra quyền của mình: `call GET "/api/identity/me?resource=record:LEAVE" $BOB`.

## 3. Biểu mẫu (Metadata)

```bash
call POST /api/metadata/forms $ADMIN '{
  "code": "LEAVE", "name": "Đơn xin nghỉ phép",
  "schema": {"fields": [
    {"key":"leaveType","label":"Loại nghỉ","type":"SELECT","options":["ANNUAL","SICK","UNPAID"],"required":true,"filterable":true},
    {"key":"fromDate","label":"Từ ngày","type":"DATE","required":true},
    {"key":"days","label":"Số ngày","type":"NUMBER","min":0.5,"max":30,"required":true},
    {"key":"reason","label":"Lý do","type":"TEXTAREA","maxLength":1000,
     "requiredIf":{"field":"days","op":"gt","value":3}},
    {"key":"citizenId","label":"Số CCCD","type":"TEXT","pattern":"^[0-9]{12}$","sensitive":true},
    {"key":"attachments","label":"Đính kèm","type":"FILE"}]}}'

# thử validate trước khi phát hành
call POST /api/metadata/forms/LEAVE/versions/1/validate $ADMIN '{"full":true,"data":{"leaveType":"ANNUAL","days":5}}'
# phát hành (bất biến từ đây)
call POST /api/metadata/forms/LEAVE/versions/1/publish $ADMIN
```

Sửa biểu mẫu sau khi phát hành: `POST /api/metadata/forms/LEAVE/versions` (tạo nháp v2 từ bản mới nhất hoặc từ `schema`
gửi kèm) → `PUT .../versions/2` → `POST .../versions/2/publish`. Hồ sơ cũ vẫn gắn v1.
Chi tiết kiểu trường và ràng buộc: [DSL-REFERENCE.md](DSL-REFERENCE.md).

## 4. Quy trình (Workflow)

```bash
call POST /api/workflow/definitions $ADMIN '{
  "code":"LEAVE_APPROVAL","name":"Duyệt nghỉ phép","formCode":"LEAVE",
  "spec":{
    "initialState":"MANAGER_REVIEW",
    "states":[
      {"code":"MANAGER_REVIEW","name":"Trưởng phòng duyệt","type":"TASK",
       "assignment":{"type":"ROLE","value":"MANAGER"},"timeout":"P3D","onTimeout":"escalate"},
      {"code":"CHECK_DAYS","type":"DECISION"},
      {"code":"DIRECTOR_REVIEW","name":"Giám đốc duyệt","type":"TASK","assignment":{"type":"ROLE","value":"DIRECTOR"}},
      {"code":"APPROVED","type":"END","outcome":"APPROVED"},
      {"code":"REJECTED","type":"END","outcome":"REJECTED"},
      {"code":"RETURNED","type":"END","outcome":"RETURNED"}],
    "transitions":[
      {"code":"approve","from":"MANAGER_REVIEW","to":"CHECK_DAYS","permission":"APPROVE","label":"Đồng ý"},
      {"code":"reject","from":"MANAGER_REVIEW","to":"REJECTED","permission":"REJECT","commentRequired":true,"label":"Từ chối"},
      {"code":"return","from":"MANAGER_REVIEW","to":"RETURNED","permission":"REJECT","commentRequired":true,"label":"Trả lại bổ sung"},
      {"code":"escalate","from":"MANAGER_REVIEW","to":"DIRECTOR_REVIEW","permission":"APPROVE","label":"Chuyển giám đốc"},
      {"code":"long","from":"CHECK_DAYS","to":"DIRECTOR_REVIEW","guard":{"field":"days","op":"gt","value":5}},
      {"code":"short","from":"CHECK_DAYS","to":"APPROVED"},
      {"code":"approve","from":"DIRECTOR_REVIEW","to":"APPROVED","permission":"APPROVE"},
      {"code":"reject","from":"DIRECTOR_REVIEW","to":"REJECTED","permission":"REJECT","commentRequired":true}]}}'
call POST /api/workflow/definitions/LEAVE_APPROVAL/versions/1/publish $ADMIN
```

Spec được kiểm tra tĩnh khi tạo/phát hành: tham chiếu trạng thái, trạng thái không đạt tới, không có END,
DECISION thiếu nhánh mặc định, chu trình DECISION, timeout/onTimeout.

## 5. Nộp hồ sơ và duyệt

```bash
REC=$(call POST /api/records $ALICE '{"formCode":"LEAVE","submit":true,
      "data":{"leaveType":"ANNUAL","fromDate":"2026-12-01","days":7,"reason":"Về quê"}}' | jq -r .id)
call GET /api/records/$REC $ALICE | jq '{recordCode,status,formVersionNo}'
```

Trạng thái đi `SUBMITTED` → `IN_PROGRESS` (quy trình được khởi tạo trong transaction riêng ngay sau commit — ADR 016).

```bash
# Hộp việc của Bob (task giao cho vai trò MANAGER)
TASK=$(call GET /api/workflow/tasks $BOB | jq -r '.items[0].id')
call GET /api/workflow/tasks/$TASK $BOB | jq '{task: .task.name, transitions: [.transitions[] | {code, permitted}], recordData}'
call POST /api/workflow/tasks/$TASK/transitions/approve $BOB '{"comment":"Đồng ý"}' | jq .task.status

# 7 ngày > 5 => DECISION rẽ sang Giám đốc
TASK2=$(call GET /api/workflow/tasks $CAROL | jq -r '.items[0].id')
call POST /api/workflow/tasks/$TASK2/transitions/approve $CAROL '{}'
call GET /api/records/$REC $ALICE | jq .status          # "APPROVED"
call GET "/api/workflow/instances?recordId=$REC" $ALICE | jq '.[0] | {versionNo,outcome,history: [.history[] | {from: .fromState, to: .toState, transitionCode, actor}]}'
```

Trả lại để bổ sung: Bob gọi `.../transitions/return` với `comment`; hồ sơ thành `RETURNED`; Alice sửa
(`PUT /api/records/$REC` với `{"data":{...},"expectedVersion":N}`) và `POST /api/records/$REC/submit` → quy trình mới
(dùng phiên bản quy trình PUBLISHED mới nhất tại thời điểm nộp lại). Chủ hồ sơ rút hồ sơ đang xử lý:
`POST /api/workflow/instances/{id}/cancel`.

Các lỗi thường gặp: `403` (không được giao/thiếu quyền — có ghi audit `DENIED`), `409` (task đã được xử lý / version cũ),
`422` (thiếu comment bắt buộc, dữ liệu không hợp lệ theo biểu mẫu — kèm `violations`).

## 6. Tìm kiếm hồ sơ

```bash
call GET "/api/records?formCode=LEAVE&status=APPROVED&data.leaveType=ANNUAL&sort=UPDATED_AT&size=20" $BOB | jq '.total, .items[].recordCode'
```

- Chỉ lọc trường động có `"filterable": true`; trường khác → `400`.
- Không có `VIEW` trên `record:<FORM>` → chỉ thấy hồ sơ của mình. Trường `sensitive` bị ẩn trong kết quả danh sách.

## 7. Tệp đính kèm

```bash
FILE=$(curl -s -X POST "$API/api/files?recordId=$REC" -H "Authorization: Bearer $ALICE" -H "X-Tenant-Id: $T" \
        -F "file=@giay-xac-nhan.pdf;type=application/pdf" | jq -r .id)
curl -s -o out.pdf "$API/api/files/$FILE/content" -H "Authorization: Bearer $BOB" -H "X-Tenant-Id: $T"
call GET "/api/files?recordId=$REC" $BOB
```

Cho phép: PDF, PNG, JPEG, TXT/CSV, DOCX, XLSX (≤ 20MB, cấu hình `drwp.file.*`). Nội dung phải khớp kiểu khai báo
(magic bytes) và đuôi tệp. Đưa ID tệp vào trường kiểu `FILE` của hồ sơ để gắn vào biểu mẫu.

## 8. Thông báo và audit

```bash
call GET /api/notifications $ALICE | jq '.items[] | {type,title,read}'
call GET /api/notifications/unread-count $BOB
call POST /api/notifications/read-all $ALICE
call GET "/api/audit/events?resourceType=workflow&outcome=DENIED" $ADMIN | jq '.items[] | {action,actor,detail}'
```

## 9. Vận hành

| Việc | Cách làm |
|---|---|
| Phát hành migration mới | Khởi động lại (với `migrate-on-startup=true`) hoặc `POST /api/platform/tenants/{id}/migrate` |
| Sự kiện nội bộ bị kẹt | `scheduled-worker` phát lại publication chưa hoàn tất > `drwp.events.replay-older-than` theo từng tenant; log cảnh báo khi backlog > `registry-backlog-warn-threshold` |
| Debezium | `docker compose ... --profile events up -d` rồi `infrastructure/debezium/register-connectors.sh`; topic `drwp.events.{record,workflow,file,notification}` |
| Event-worker | `--spring.profiles.active=event-worker`; tenant lấy từ schema nguồn Debezium; lệch tenant → DLT `<topic>-dlt` + log `SECURITY` |
| Health | `/actuator/health` (có `controlPlane`); `/actuator/prometheus`, `/actuator/modulith` cần vai trò platform-admin |
| Log | Mỗi dòng có `[tenant=...,corr=...]`; header `X-Correlation-Id` được trả về |

## 10. Mở rộng

**Thêm module mới** (ví dụ `reporting`):
1. Tạo `modules/reporting` (artifact `module-reporting`), package gốc `vn.drwp.reporting` với `package-info.java`
   `@ApplicationModule`, package `api` có `@NamedInterface("api")`.
2. Bảng dùng tiền tố riêng (`rep_`), migration ở `src/main/resources/db/tenant/reporting/V1__*.sql`,
   khai báo bean `new TenantSchemaModule("reporting", <order>)`. Bổ sung tiền tố vào `TableOwnershipTest`.
3. Chỉ dùng contract `api` của module khác; dữ liệu module khác lấy qua contract hoặc read model riêng (không JOIN).
4. Sự kiện nội bộ: record trong `api` implement `TenantScopedEvent`; listener dùng `@ApplicationModuleListener`.
   Sự kiện ra ngoài: `OutboxWriter.append(...)` trong cùng transaction.
5. Thêm artifact vào `pom.xml` gốc và `business-app/pom.xml`; chạy `mvn test` để `ModularityTest` kiểm tra ranh giới.

**Thêm handler Kafka** cho event-worker: cài `ExternalEventHandler` (bean `@ConditionalOnRuntimeRole(EVENT_WORKER)`);
inbox, tenant context và transaction đã được `ExternalEventConsumer` lo.

## 11. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| `400 TENANT_REQUIRED` | Thiếu header `X-Tenant-Id` (trừ `/api/platform/**`, `/api/me/**`) |
| `403 TENANT_ACCESS_DENIED` | Người dùng không có membership ACTIVE, hoặc tenant không ACTIVE |
| Hồ sơ đứng ở `SUBMITTED` | Chưa có quy trình ACTIVE + PUBLISHED cho `formCode` (audit `WORKFLOW_NOT_CONFIGURED`), hoặc listener lỗi — xem bảng `event_publication` của tenant |
| `TenantIsolationException: Connection requested without tenant context` | Code chạy ngoài request/listener mà không bọc `TenantContext.runAs(...)` |
| Khởi động chậm ~30s | Một cụm trong `drwp.tenancy.clusters` không truy cập được (Hikari chờ connection) |
