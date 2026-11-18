# Tham chiếu DSL: biểu mẫu, điều kiện, quy trình

Mọi cấu hình động là JSON thuần, được parse theo **allowlist** (tài liệu 02 mục 1.5): khóa lạ bị từ chối,
không có biểu thức tự do (không Java/SQL/SpEL/JavaScript).

## 1. Điều kiện (Condition) — dùng cho `requiredIf` và `guard`

```json
{"field": "days", "op": "gt", "value": 5}
{"all": [ <cond>, <cond> ]}
{"any": [ <cond>, <cond> ]}
{"not": <cond>}
```

| `op` | Ý nghĩa | `value` |
|---|---|---|
| `eq`, `ne` | bằng / khác (số so theo giá trị: `5` = `5.0` = `"5"`) | scalar |
| `gt`, `gte`, `lt`, `lte` | so sánh số; chuỗi so theo từ điển (ngày ISO `yyyy-MM-dd` đúng thứ tự) | scalar |
| `in`, `notIn` | thuộc / không thuộc danh sách | mảng 1–100 scalar |
| `contains` | chuỗi chứa chuỗi con; hoặc mảng chứa phần tử | scalar |
| `empty`, `notEmpty` | null, chuỗi trắng, mảng/đối tượng rỗng | (không có) |

- `field`: đường dẫn tối đa 3 cấp, mỗi đoạn `[a-zA-Z][a-zA-Z0-9_]*` (ví dụ `applicant.department`).
- Giới hạn: độ sâu 6, tối đa 64 node, chuỗi hằng ≤ 500 ký tự, scalar chỉ là string/number/boolean.

## 2. Biểu mẫu (`schema`)

```json
{"fields": [ { "key": "...", "label": "...", "type": "...", ... } ]}
```

| Khóa | Áp dụng | Ghi chú |
|---|---|---|
| `key` | mọi kiểu | `^[a-z][a-zA-Z0-9_]{0,49}$`, duy nhất |
| `label`, `description` | mọi kiểu | hiển thị |
| `type` | — | xem bảng dưới |
| `required` | mọi kiểu | bắt buộc khi nộp (`full`) |
| `requiredIf` | mọi kiểu | Condition; chỉ tham chiếu trường có trong biểu mẫu |
| `options` | SELECT, MULTISELECT | 1–200 giá trị duy nhất |
| `minLength`, `maxLength` | TEXT, TEXTAREA, EMAIL | mặc định max 500 (TEXT) / 10 000 (TEXTAREA) |
| `min`, `max` | NUMBER, INTEGER | |
| `pattern` | TEXT, TEXTAREA | ≤ 200 ký tự; cấm backreference/lookaround; khớp có timeout 50 ms |
| `filterable` | mọi kiểu | cho phép lọc `data.<key>=` trong tìm kiếm |
| `sensitive` | mọi kiểu | ẩn khỏi kết quả danh sách/tìm kiếm |

| `type` | Giá trị hợp lệ |
|---|---|
| `TEXT`, `TEXTAREA`, `EMAIL` | chuỗi |
| `NUMBER` | số; `INTEGER` | số nguyên |
| `BOOLEAN` | true/false |
| `DATE` | `yyyy-MM-dd`; `DATETIME` | ISO-8601 có offset, ví dụ `2026-12-01T08:00:00+07:00` |
| `SELECT` | một trong `options`; `MULTISELECT` | mảng giá trị khác nhau thuộc `options` |
| `FILE` | mảng ≤ 20 ID tệp (UUID) | |
| `USER` | subject người dùng (dùng cho phân công `FIELD`) | |

Validate: **lưu nháp** chỉ kiểm tra kiểu/ràng buộc của trường có giá trị; **nộp** kiểm tra đầy đủ (kể cả `required`,
`requiredIf`) theo đúng phiên bản biểu mẫu hồ sơ đang gắn. Khóa dữ liệu không có trong biểu mẫu bị từ chối.

## 3. Quy trình (`spec`)

```json
{"initialState": "A", "states": [ ... ], "transitions": [ ... ]}
```

### Trạng thái

| Khóa | Ghi chú |
|---|---|
| `code` | `^[A-Z][A-Z0-9_]{0,49}$`, duy nhất |
| `name` | tên task hiển thị |
| `type` | `TASK` (chờ người), `DECISION` (rẽ nhánh tự động), `END` |
| `assignment` (TASK) | `{"type":"ROLE","value":"MANAGER"}`, `{"type":"USER","value":"<subject>"}`, `{"type":"SUBMITTER"}`, `{"type":"FIELD","value":"approver"}` (trường USER của hồ sơ) |
| `timeout` + `onTimeout` (TASK) | ISO-8601 `PT1M`..`P365D`; `onTimeout` là code transition đi ra từ trạng thái này, hệ thống tự kích hoạt (actor `system:timeout`) |
| `outcome` (END) | `APPROVED`, `REJECTED`, `RETURNED` (trả lại để bổ sung, nộp lại tạo instance mới), `CANCELLED` |

### Transition

| Khóa | Ghi chú |
|---|---|
| `code` | `^[a-z][a-zA-Z0-9_]{0,49}$`, duy nhất theo `from` |
| `from`, `to` | code trạng thái |
| `permission` | **bắt buộc** với transition từ TASK: action cần có trên `wf:<WORKFLOW_CODE>` (ví dụ `APPROVE`, `REJECT`) |
| `guard` | Condition trên dữ liệu hồ sơ. Từ TASK: chỉ được chọn khi guard đúng. Từ DECISION: guard đầu tiên đúng thắng |
| `commentRequired` | yêu cầu `comment` khi thực hiện |
| `label` | nhãn nút |

Luật kiểm tra tĩnh: trạng thái đầu không phải END; mọi trạng thái đạt tới được; có END đạt tới được; TASK có ít nhất một
transition ra; END không có transition ra; DECISION có đúng một transition mặc định (không guard) và đặt **cuối cùng**;
không có chu trình chỉ gồm DECISION; tối đa 50 trạng thái / 200 transition; runtime giới hạn 20 bước tự động liên tiếp.

### Điều kiện để một người thực hiện transition

1. Thuộc tenant (membership) → 2. task đang OPEN và đúng trạng thái hiện tại của instance → 3. được giao (USER trùng
subject, hoặc có vai trò ROLE) → 4. bitmask có `permission` trên `wf:<CODE>` → 5. guard đúng → 6. comment nếu bắt buộc.
Bị từ chối ở bước 3–5 được ghi audit `WORKFLOW_TRANSITION / DENIED`.

### Phiên bản

Instance gắn cố định phiên bản quy trình lúc khởi tạo. Phát hành V2 chỉ áp dụng cho lần nộp mới.
Mỗi loại hồ sơ (`formCode`) có tối đa một quy trình `ACTIVE`; đổi quy trình: `PATCH /api/workflow/definitions/{code}`
với `"status":"ARCHIVED"` rồi tạo quy trình mới.
