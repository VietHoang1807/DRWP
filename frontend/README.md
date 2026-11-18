# DRWP Frontend

Giao diện web của **DRWP: Nền tảng Hồ sơ & Quy trình động** (backend: `../` trong thư mục `wp`, Spring Boot modular monolith).
React 19 · Vite 8 · TypeScript 6 · Tailwind 4 · react-router 8 · TanStack Query 5 · Playwright.

Mã được viết lại từ [dynaFlow-platform-fe](https://github.com/VietHoang1807/dynaFlow-platform-fe): giữ hệ token màu, font và các primitive UI,
thay toàn bộ dữ liệu giả bằng API thật.

## Chạy nhanh

```bash
# 1. Backend (thư mục wp), profile dev có POST /dev/token
docker compose -f infrastructure/compose/docker-compose.yml up -d postgres-a
mvn -pl business-app spring-boot:run -Dspring-boot.run.profiles=dev

# 2. Frontend (thư mục wp/frontend)
npm install
npm run seed      # tạo đơn vị demo, vai trò, alice/bob/carol, biểu mẫu LEAVE, quy trình LEAVE_APPROVAL, hồ sơ mẫu
npm run dev       # http://localhost:5173
```

Vite proxy `/api`, `/dev`, `/actuator` sang `DRWP_BACKEND_URL` (mặc định `http://localhost:8080`), nên backend **không cần cấu hình CORS**.

| Lệnh | Việc |
|---|---|
| `npm run dev` | Dev server + proxy |
| `npm run build` | `tsc -b` rồi `vite build` |
| `npm run lint` | oxlint (`src`, `e2e`, `scripts`; bỏ qua `src/vendor`) |
| `npm run seed` | Dữ liệu demo, chạy lại được (bỏ qua 409) |
| `npm run e2e` | Playwright; ảnh chụp ở `e2e/screenshots/`, báo cáo ở `e2e/report/` |
| `npm run e2e:landing` | Chỉ test trang giới thiệu (không cần backend) |

### Tài khoản mẫu (chế độ dev)

| Tài khoản | Vai trò | Dùng để |
|---|---|---|
| `platform-root` | Quản trị nền tảng | Tạo/tạm dừng/kích hoạt đơn vị, chạy migration |
| `admin-demo` | TENANT_ADMIN của `demo` | Biểu mẫu, quy trình, vai trò, thành viên, nhật ký |
| `alice` | EMPLOYEE | Lập và nộp hồ sơ nghỉ phép |
| `bob` | MANAGER | Duyệt cấp 1 (`wf:LEAVE_APPROVAL`) |
| `carol` | DIRECTOR | Duyệt cấp 2 khi nghỉ trên 5 ngày, xem mọi hồ sơ, xem nhật ký |

## Cấu hình (`.env`, xem `.env.example`)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `VITE_AUTH_MODE` | `dev` | `dev`: đăng nhập qua `POST /dev/token`. `oidc`: Keycloak Authorization Code + PKCE |
| `VITE_API_BASE_URL` | rỗng | Để trống khi dùng proxy. Đặt URL đầy đủ nếu khác origin (khi đó backend cần CORS) |
| `VITE_OIDC_ISSUER` | `http://localhost:8180/realms/drwp` | Realm Keycloak |
| `VITE_OIDC_CLIENT_ID` | `drwp-web` | Public client, redirect `/auth/callback` |
| `DRWP_BACKEND_URL` | `http://localhost:8080` | Đích proxy Vite và `npm run seed` |

Token chỉ giữ trong bộ nhớ. Ở chế độ dev, tham số đăng nhập lưu trong `sessionStorage` để F5 không bị đăng xuất; đơn vị đang chọn lưu ở `sessionStorage['drwp.tenant']`.

## Kiến trúc

```
src/
  api/          # client theo module backend: platform, identity, metadata, records, workflow, misc (notification, audit, file)
  auth/         # session store (useSyncExternalStore), dev token / OIDC PKCE, guard route
  components/
    forms/DynamicForm.tsx     # render 12 kiểu trường từ FormSchema
    layout/                   # AppShell, Header (đổi đơn vị, thông báo), Sidebar (ẩn mục theo quyền)
    ui/                       # Button, Input, Card, Badge, Primitives, Skeleton
    WorkflowGraph.tsx, WorkflowTimeline.tsx
  lib/          # http (Problem Details, X-Tenant-Id), config, format vi-VN, i18n (chuỗi tập trung)
  screens/      # một thư mục mỗi nhóm nghiệp vụ, mỗi màn hình lazy() theo route
  vendor/scrollcraft/         # engine scroll-craft (MIT), chỉ dùng cho trang giới thiệu
```

Nguyên tắc (react-best-practices):

- **Cách ly đơn vị trong cache**: mọi query key bắt đầu bằng `['t', tenantId, ...]`; đổi đơn vị gọi `removeQueries(['t'])`.
- **Không waterfall**: kiểm tra quyền nhiều resource song song bằng `useQueries`; mỗi khối Dashboard tự tải.
- **Code splitting**: mỗi màn hình là một chunk `lazy()`; engine scroll-craft chỉ nạp động ở route `/`.
- **Skeleton theo khối** chỉ ở lần tải đầu (`isPending`); làm mới dữ liệu đã có dùng thanh mảnh trên đầu (`useIsFetching`). Tôn trọng `prefers-reduced-motion`.
- **Lỗi**: backend trả RFC 9457 Problem Details → `ApiError`; 401 → về đăng nhập; 403 `TENANT_ACCESS_DENIED` → chọn lại đơn vị; 422 → gắn lỗi vào đúng trường biểu mẫu; 409 → báo dữ liệu đã đổi.
- Ẩn menu/nút theo quyền chỉ là UX; quyền luôn được backend kiểm tra.

## Màn hình và API

| Route | Màn hình | API chính | Nguồn |
|---|---|---|---|
| `/` | Trang giới thiệu (scroll-craft) | (không) | **Mới** |
| `/login` | Đăng nhập dev/OIDC | `POST /dev/token`, Keycloak | Viết lại |
| `/select-tenant` | Chọn đơn vị | `GET /api/me/tenants` | **Mới** |
| `/app` | Tổng quan | `/api/workflow/tasks`, `/api/records`, `/api/notifications` | Viết lại (Dashboard) |
| `/app/records` | Danh sách hồ sơ, lọc theo trường `filterable` | `GET /api/records?data.<field>=` | Viết lại (DynamicRecords) |
| `/app/records/new` | Lập hồ sơ từ biểu mẫu động | `GET /api/metadata/forms/{code}/published`, `POST /api/records` | **Mới** |
| `/app/records/:id` | Chi tiết: dữ liệu, tệp, quy trình, lịch sử | `/api/records/{id}`, `/submit`, `/cancel`, `/revisions`, `/api/files`, `/api/workflow/instances` | **Mới** |
| `/app/tasks` | Hộp việc & phê duyệt | `/api/workflow/tasks`, `/transitions/{code}` | Viết lại (TasksApprovals) |
| `/app/forms`, `/app/forms/:code` | Biểu mẫu + trình dựng (trực quan / JSON, xem trước, kiểm tra, phát hành) | `/api/metadata/forms/**` | Viết lại (FormBuilder) |
| `/app/workflows`, `/app/workflows/:code` | Quy trình + thiết kế (sơ đồ từ spec, JSON, phiên bản) | `/api/workflow/definitions/**` | Viết lại (WorkflowAutomation + VisualDesigner) |
| `/app/roles` | Ma trận quyền 3 trạng thái (trống / cho phép / cấm) | `/api/identity/roles/**` | Viết lại (RoleMatrix) |
| `/app/members` | Thành viên đơn vị | `/api/identity/members/**` | **Mới** |
| `/app/audit` | Nhật ký kiểm toán | `GET /api/audit/events` | Viết lại (AuditLogs) |
| `/app/notifications` | Thông báo | `/api/notifications/**` | **Mới** |
| `/app/platform/tenants` | Quản trị đơn vị (platform admin) | `/api/platform/tenants/**` | **Mới** |
| `/app/batch-jobs`, `/app/auth-sources` | Giữ giao diện cũ, nhãn **Sắp có** | (backend chưa có API) | Giữ nguyên, dữ liệu minh hoạ |

### Màn hình bổ sung (chưa có trong repo FE gốc)

Repo gốc chưa đủ màn hình để phủ hết nghiệp vụ backend, nên đã thêm:

1. **Trang giới thiệu** `/`: scroll-craft, brief ở `docs/landing/BRIEF.md`.
2. **Chọn đơn vị** `/select-tenant`: người dùng thuộc nhiều đơn vị; tự chọn nếu chỉ có một.
3. **Lập hồ sơ** và **Chi tiết hồ sơ**: biểu mẫu động, lưu nháp/nộp, sửa khi bị trả lại, rút/huỷ, tệp đính kèm, timeline quy trình, lịch sử revision.
4. **Thành viên**: thêm/gỡ thành viên, gán vai trò.
5. **Thông báo**: tất cả/chưa đọc, đánh dấu đã đọc, đi tới hồ sơ.
6. **Quản trị đơn vị (nền tảng)**: tạo đơn vị (schema riêng), tạm dừng/kích hoạt, chạy lại migration.
7. **404**.

## Trang giới thiệu (scroll-craft)

- Engine: `src/vendor/scrollcraft/` (nguồn [nateherkai/scroll-craft](https://github.com/nateherkai/scroll-craft), MIT). Reset toàn cục của engine được thu hẹp vào `.sc-root`; khi rời trang gọi `destroy()` và xoá `--sc-canvas`.
- 5 act: hero nhiều lớp (giấy kẻ, giao diện thật, bìa hồ sơ, kẹp giấy + dấu) → biểu mẫu tự dựng (pin) → **đường duyệt ngang, dấu ĐÃ DUYỆT đóng theo cuộn** (pan, đỉnh) → nhiều đơn vị (nền chuyển tối) → kết.
- Chỉ animate `transform`/`opacity`; có bố cục riêng cho điện thoại; `prefers-reduced-motion` hiển thị trạng thái đã ổn định.
- Ứng dụng (`/app`) không dùng scroll-craft, chỉ dùng chung màu và font.

## Kiểm thử

`npm run e2e` (Playwright, tự bật Vite nếu chưa chạy):

- `e2e/landing.spec.ts`: hero, dấu đóng ở 90% act đường duyệt, reduced motion, không tràn ngang trên điện thoại. Không cần backend.
- `e2e/flow.spec.ts`: alice nộp 8 ngày → bob duyệt → carol duyệt → hồ sơ **Đã phê duyệt**; chụp các màn hình quản trị.
  Cần backend profile dev và `npm run seed`; tự bỏ qua nếu backend chưa chạy.

## Giới hạn đã biết

- Batch job và nguồn xác thực chưa có API backend: giữ giao diện, đánh dấu **Sắp có**.
- Trình thiết kế quy trình hiển thị sơ đồ từ JSON; chưa kéo thả để thêm bước.
- Trường `USER` nhập mã định danh (sub) bằng tay; chưa có tìm kiếm người dùng.
- Chưa có i18n đa ngôn ngữ: chuỗi đã gom ở `src/lib/i18n.ts` và trong từng màn hình, chỉ tiếng Việt.
- Font tải từ Google Fonts; môi trường không có Internet sẽ dùng font hệ thống.
