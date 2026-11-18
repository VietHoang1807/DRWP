# gateway-app (chưa triển khai)

Deployable #1 theo ADR 013. **Tạm thời để trống** — chưa nằm trong Maven reactor.

`business-app` đã tự xác thực JWT (issuer, audience, chữ ký, hạn dùng) và tự xác định tenant từ membership,
nên chạy được trực tiếp mà không cần gateway trong môi trường dev/test (tài liệu 02 mục 4: xác thực ở cả hai lớp).

## Phạm vi dự kiến (ADR 013)

- Spring Cloud Gateway (WebFlux) làm edge: xác thực JWT, rate limit bằng Redis, giới hạn kích thước body.
- Loại bỏ header nội bộ do client gửi (ví dụ `X-Internal-*`); **không** thêm header tenant tin cậy — business-app
  không tin header tenant, chỉ coi `X-Tenant-Id` là lựa chọn và luôn kiểm tra membership.
- STOMP WebSocket với Redis pub/sub giữa các node: tiêu thụ sự kiện `NotificationCreated`
  (topic `drwp.events.notification`, xem outbox của module Notification) để đẩy thông báo realtime.
- Không chứa logic nghiệp vụ, phân quyền đối tượng hay truy cập DB nghiệp vụ.

## Khi triển khai

1. Thêm `<module>gateway-app</module>` vào `pom.xml` gốc.
2. Route `/api/**` -> business-app (role `api`), giữ nguyên header `Authorization`.
3. Có thể thay bằng Kong/nginx mà không sửa business-app; khi đó phần WebSocket phải chuyển chỗ.
